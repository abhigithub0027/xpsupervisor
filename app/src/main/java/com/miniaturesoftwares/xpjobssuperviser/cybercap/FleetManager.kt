package com.miniaturesoftwares.xpjobssuperviser.cybercap

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds live links to every cap in the fleet at once.
 *
 * This is the one part of the supervisor app that is genuinely new rather than
 * ported. The recorder app's manager is single-device by design; this one runs
 * N independent [CyberCapBleClient] instances side by side and presents them as
 * one fleet.
 *
 * Three constraints shape it, all learned from the platform rather than chosen:
 *
 *  1. **Android caps concurrent GATT links.** The stack allows roughly seven;
 *     some handsets fewer. Past the cap, connects fail with an opaque error
 *     rather than queueing, so [MAX_CONCURRENT_LINKS] is enforced here and
 *     surplus devices wait their turn.
 *  2. **Connecting several radios at once destabilises the stack.** Links are
 *     opened one at a time, [CONNECT_STAGGER_MS] apart - the same lesson the
 *     recorder app learned opening several USB cameras together.
 *  3. **Each device bonds separately.** A cap only accepts a new pairing during
 *     its pairing window, so a fleet is built up one sticker at a time.
 */
@Singleton
class FleetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: FleetDeviceStore,
) {
    /** One cap's live state, as the fleet screen renders it. */
    data class DeviceState(
        val device: PairedDevice,
        val link: LinkState = LinkState.IDLE,
        val heartbeat: CyberCapHeartbeat? = null,
        val lastAck: CyberCapAck? = null,
        val lastAckAtMs: Long = 0L,
    ) {
        /**
         * A refusal worth putting on screen.
         *
         * Only recent ones: a device that refused ten minutes ago and has since
         * been fixed should not still be showing the old complaint.
         */
        val activeRefusal: CyberCapAck?
            get() = lastAck?.takeIf {
                !it.ok && System.currentTimeMillis() - lastAckAtMs < REFUSAL_VISIBLE_MS
            }

        val isConnected: Boolean get() = link == LinkState.CONNECTED
        val isRecording: Boolean get() = heartbeat?.recording == true

        /** Silence is unambiguous: the cap emits every second unconditionally. */
        val isStale: Boolean
            get() {
                val hb = heartbeat ?: return link == LinkState.CONNECTED
                return System.currentTimeMillis() - hb.receivedAtMs > STALE_AFTER_MS
            }

        fun canStart(): Boolean = isConnected && !isStale && heartbeat?.recording == false
        fun canStop(): Boolean = isConnected && !isStale && heartbeat?.recording == true
    }

    enum class LinkState { IDLE, WAITING_SLOT, CONNECTING, CONNECTED, STALE }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** deviceId -> transport. Only devices holding a slot have a client. */
    private val clients = mutableMapOf<String, CyberCapBleClient>()

    /**
     * Raised for every command reply. The fleet list shows a refusal on the
     * row, but the supervisor tapped a button and deserves an immediate answer
     * too - especially when the row is scrolled off screen.
     */
    var onAck: ((deviceId: String, ack: CyberCapAck) -> Unit)? = null

    /** Delivers pages requested with [requestRecordings]. */
    var onRecordings: ((deviceId: String, page: RecordingsPage) -> Unit)? = null

    private val _fleet = MutableStateFlow<List<DeviceState>>(emptyList())
    val fleet: StateFlow<List<DeviceState>> = _fleet.asStateFlow()

    val connectedCount: Int get() = _fleet.value.count { it.isConnected }
    val recordingCount: Int get() = _fleet.value.count { it.isRecording }

    init {
        _fleet.value = store.all().map { DeviceState(it) }

        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF ->
                            disconnectAll()
                        BluetoothAdapter.STATE_ON -> connectAll()
                    }
                }
            },
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        )

        // One ticker for the whole fleet rather than one per device: with a
        // dozen caps that difference is the whole cost of the screen.
        scope.launch {
            while (true) {
                delay(TICK_MS)
                reapStaleLinks()
                fillFreeSlots()
            }
        }
    }

    // ---------------- pairing ----------------

    /**
     * Add a scanned sticker to the fleet.
     *
     * Unlike the recorder app there is no "replace" decision - a supervisor
     * scanning a second cap means they want both.
     */
    fun addDevice(payload: DeviceQrPayload): AddResult {
        if (!hasPermissions()) return AddResult.MissingPermission
        if (!bluetoothEnabled()) return AddResult.BluetoothOff
        if (store.isPaired(payload.deviceId)) return AddResult.AlreadyPaired

        store.put(
            PairedDevice(
                deviceId = payload.deviceId,
                name = payload.name,
                lastAddress = "",
                pairedAtMs = System.currentTimeMillis(),
            )
        )
        refreshFromStore()
        connectAll()
        return AddResult.Added
    }

    enum class AddResult { Added, AlreadyPaired, MissingPermission, BluetoothOff }

    /**
     * Drop a cap from the fleet.
     *
     * Tells the device to unpair first. Bonds are per-side: if only the phone
     * forgets, the cap keeps a key for a peer it can no longer authenticate and
     * then refuses to pair again, which cannot be cleared from the app.
     */
    fun removeDevice(deviceId: String) {
        clients[deviceId]?.let { client ->
            runCatching {
                client.sendJson(JSONObject().apply {
                    put("type", "command")
                    put("command", "unpair")
                })
            }
        }
        scope.launch {
            delay(UNPAIR_SETTLE_MS)
            releaseSlot(deviceId)
            store.remove(deviceId)
            refreshFromStore()
            fillFreeSlots()
        }
    }

    // ---------------- connections ----------------

    fun connectAll() {
        if (!hasPermissions() || !bluetoothEnabled()) return
        fillFreeSlots()
    }

    fun disconnectAll() {
        clients.keys.toList().forEach { releaseSlot(it) }
        _fleet.value = _fleet.value.map { it.copy(link = LinkState.IDLE, heartbeat = null) }
    }

    /**
     * Open links for devices that do not have one, up to the platform cap, one
     * at a time.
     */
    private fun fillFreeSlots() {
        if (!hasPermissions() || !bluetoothEnabled()) return
        if (connecting) return

        val waiting = _fleet.value.filter { clients[it.device.deviceId] == null }
        if (waiting.isEmpty()) return

        val free = MAX_CONCURRENT_LINKS - clients.size
        if (free <= 0) {
            // Nothing to do until a link drops. Say so on screen rather than
            // leaving those rows looking merely disconnected.
            update(waiting.map { it.device.deviceId }) { it.copy(link = LinkState.WAITING_SLOT) }
            return
        }

        val next = waiting.first().device
        connecting = true
        update(listOf(next.deviceId)) { it.copy(link = LinkState.CONNECTING) }

        val client = CyberCapBleClient(context, listenerFor(next.deviceId))
        clients[next.deviceId] = client
        client.connectToDeviceId(next.deviceId)

        scope.launch {
            delay(CONNECT_STAGGER_MS)
            connecting = false
            fillFreeSlots()
        }
    }

    private var connecting = false

    private fun releaseSlot(deviceId: String) {
        clients.remove(deviceId)?.disconnect()
        update(listOf(deviceId)) { it.copy(link = LinkState.IDLE, heartbeat = null) }
    }

    /**
     * A link whose heartbeats stopped is holding a slot it is not using. Drop
     * it so a waiting device can take the slot.
     */
    private fun reapStaleLinks() {
        val dead = _fleet.value.filter {
            clients[it.device.deviceId] != null &&
                it.heartbeat != null &&
                System.currentTimeMillis() - it.heartbeat.receivedAtMs > LINK_DEAD_AFTER_MS
        }
        dead.forEach {
            Log.w(TAG, "reaping dead link ${it.device.deviceId}")
            releaseSlot(it.device.deviceId)
        }
        // Mark the merely-quiet ones without dropping them.
        update(_fleet.value.filter { it.isStale && it.link == LinkState.CONNECTED }
            .map { it.device.deviceId }) { it.copy(link = LinkState.STALE) }
    }

    // ---------------- commands ----------------

    fun startRecording(deviceId: String, taskId: String, metadata: JSONObject?): Boolean {
        val client = clients[deviceId] ?: return false
        val state = _fleet.value.firstOrNull { it.device.deviceId == deviceId }
        if (state?.isRecording == true) return false
        return client.sendJson(JSONObject().apply {
            put("type", "command")
            put("command", "start_recording")
            put("task_id", taskId)
            if (metadata != null) put("metadata", metadata)
        })
    }

    /**
     * Ask a device for one page of its recording inventory.
     *
     * Paged rather than all-at-once because the reply travels over BLE: a cap
     * that has been out of coverage for days can hold hundreds of segments,
     * and a single reply would take minutes to clock out while showing nothing.
     * The page arrives via [onRecordings].
     */
    fun requestRecordings(deviceId: String, offset: Int = 0, limit: Int = RECORDINGS_PAGE): Boolean {
        val client = clients[deviceId] ?: return false
        return client.sendJson(JSONObject().apply {
            put("type", "command")
            put("command", "list_recordings")
            put("offset", offset)
            put("limit", limit)
        })
    }

    fun stopRecording(deviceId: String): Boolean {
        val client = clients[deviceId] ?: return false
        return client.sendJson(JSONObject().apply {
            put("type", "command")
            put("command", "stop_recording")
        })
    }

    /**
     * Start every cap that can start, returning the ids that accepted.
     *
     * Sends are spaced: pushing a dozen writes into the stack at once is the
     * same mistake as connecting them all at once.
     */
    fun startAll(taskId: String, metadata: JSONObject?, onDone: (List<String>) -> Unit) {
        scope.launch {
            val started = mutableListOf<String>()
            _fleet.value.filter { it.canStart() }.forEach { state ->
                if (startRecording(state.device.deviceId, taskId, metadata)) {
                    started += state.device.deviceId
                }
                delay(COMMAND_STAGGER_MS)
            }
            onDone(started)
        }
    }

    fun stopAll(onDone: (List<String>) -> Unit) {
        scope.launch {
            val stopped = mutableListOf<String>()
            _fleet.value.filter { it.canStop() }.forEach { state ->
                if (stopRecording(state.device.deviceId)) stopped += state.device.deviceId
                delay(COMMAND_STAGGER_MS)
            }
            onDone(stopped)
        }
    }

    // ---------------- transport callbacks ----------------

    private fun listenerFor(deviceId: String) = object : CyberCapBleClient.Listener {
        override fun onState(state: CyberCapBleClient.State, detail: String) {
            val link = when (state) {
                CyberCapBleClient.State.READY -> LinkState.CONNECTED
                CyberCapBleClient.State.IDLE -> LinkState.IDLE
                else -> LinkState.CONNECTING
            }
            update(listOf(deviceId)) { it.copy(link = link) }

            // A link that went idle has given up its slot; let the next device
            // in line take it.
            if (state == CyberCapBleClient.State.IDLE) {
                clients.remove(deviceId)
                scope.launch { fillFreeSlots() }
            }
        }

        override fun onVerified(verifiedId: String, address: String) {
            store.updateAddress(deviceId, address)
            refreshFromStore()
        }

        override fun onLine(json: JSONObject) {
            CyberCapHeartbeat.parse(json, System.currentTimeMillis())?.let { hb ->
                update(listOf(deviceId)) { it.copy(heartbeat = hb, link = LinkState.CONNECTED) }
                return
            }
            CyberCapAck.parse(json)?.let { ack ->
                // Logged unconditionally: a refusal is the device explaining
                // exactly why it will not record, and it is the only place that
                // reason exists.
                Log.i(TAG, "[$deviceId] ack ${ack.command} ok=${ack.ok} detail=${ack.detail}")
                update(listOf(deviceId)) {
                    it.copy(lastAck = ack, lastAckAtMs = System.currentTimeMillis())
                }
                onAck?.invoke(deviceId, ack)
            }
            RecordingsPage.parse(json)?.let { page ->
                Log.i(TAG, "[$deviceId] recordings ${page.offset}+${page.items.size}/${page.total}")
                onRecordings?.invoke(deviceId, page)
                return
            }
            // preview_frame is ignored here: the fleet screen shows status for
            // many caps, not video for one. The detail screen subscribes
            // separately when a supervisor opens a single device.
        }

        override fun onLog(message: String) {
            Log.i(TAG, "[$deviceId] $message")
        }
    }

    // ---------------- helpers ----------------

    private fun refreshFromStore() {
        val known = _fleet.value.associateBy { it.device.deviceId }
        _fleet.value = store.all().map { device ->
            known[device.deviceId]?.copy(device = device) ?: DeviceState(device)
        }
    }

    private fun update(deviceIds: List<String>, transform: (DeviceState) -> DeviceState) {
        if (deviceIds.isEmpty()) return
        _fleet.value = _fleet.value.map {
            if (it.device.deviceId in deviceIds) transform(it) else it
        }
    }

    fun hasPermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun bluetoothEnabled(): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    companion object {
        private const val TAG = "FleetManager"

        /**
         * Concurrent GATT links Android will sustain.
         *
         * The platform limit is around seven and is not queryable; exceeding it
         * fails the connect with an opaque status rather than waiting. Held one
         * below a nominal eight so a pairing attempt always has room.
         */
        const val MAX_CONCURRENT_LINKS = 7

        /**
         * Segments per page. The device clamps this to 50; 20 keeps a page
         * small enough to arrive quickly over BLE while still filling a
         * screen in one round trip.
         */
        const val RECORDINGS_PAGE = 20

        /** Gap between opening links. Opening several at once wedges the stack. */
        private const val CONNECT_STAGGER_MS = 1_200L

        /** Gap between fleet-wide command writes. */
        private const val COMMAND_STAGGER_MS = 250L

        private const val TICK_MS = 1_000L

        /** Three missed beats: tolerant of one dropped notification. */
        const val STALE_AFTER_MS = 3_500L

        /** Past this the link is not merely quiet, it is gone - free the slot. */
        private const val LINK_DEAD_AFTER_MS = 15_000L

        private const val UNPAIR_SETTLE_MS = 800L

        /** How long a refusal stays on the row before it is treated as history. */
        const val REFUSAL_VISIBLE_MS = 30_000L
    }
}
