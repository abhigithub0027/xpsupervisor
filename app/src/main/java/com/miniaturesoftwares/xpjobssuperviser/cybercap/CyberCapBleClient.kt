package com.miniaturesoftwares.xpjobssuperviser.cybercap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID

/**
 * BLE transport for a cyber-cap device. Policy (which device, when to reconnect)
 * lives in [CyberCapConnectionManager]; this class only moves bytes.
 *
 * Two rules here are load-bearing, each learned the hard way on hardware:
 *
 *  1. Match on the service UUID, never the advertised name. The panel also runs
 *     a classic UART Bluetooth module under the SAME name; it connects fine and
 *     then turns out to host no GATT service at all.
 *  2. Verify device_id from the device's own heartbeat before trusting it - a
 *     scan can find more than one cyber-cap in a room, and this is the only
 *     way to know it found the right one. A mismatch retries the next scanned
 *     candidate rather than accepting whichever answered first.
 *
 * No bonding: the device's RX characteristic is plain write, not
 * encrypt-write, so nothing here ever needs to wait on Android's own pairing
 * flow. That is a deliberate simplification, not an oversight - see
 * panel.ble.require_bonding in the device config for why.
 */
@SuppressLint("MissingPermission")
class CyberCapBleClient(
    private val context: Context,
    private val listener: Listener,
) {

    enum class State { IDLE, SCANNING, CONNECTING, DISCOVERING, VERIFYING, READY }

    interface Listener {
        fun onState(state: State, detail: String)
        fun onLine(json: JSONObject)
        fun onVerified(deviceId: String, address: String)
        fun onLog(message: String)
    }

    companion object {
        private const val TAG = "CyberCapBle"

        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val REQUESTED_MTU = 517
        private const val ATT_HEADER_BYTES = 3
        private const val DEFAULT_PAYLOAD = 20

        /**
         * Conservative write size, deliberately far below the negotiated MTU.
         *
         * Two separate limits bite above this. Android refuses any characteristic
         * value over 512 bytes (GATT_MAX_ATTR_LEN) by throwing rather than
         * returning an error, and the peer independently rejected a 512-byte
         * write with GATT_INVALID_ATTRIBUTE_LENGTH (13) because BlueZ enforces
         * its own per-connection MTU, which need not match what Android reports.
         *
         * Since records are reassembled from newline-delimited chunks anyway,
         * write size only affects throughput - and these payloads are under a
         * kilobyte, so a handful of extra writes costs nothing. Being safely
         * inside every stack's limit matters far more than being maximal.
         */
        private const val MAX_WRITE_BYTES = 180
        private const val MAX_LINE_BYTES = 64 * 1024
        private const val NEWLINE: Byte = 0x0A
        private const val CARRIAGE_RETURN: Byte = 0x0D

        private const val SCAN_COLLECT_MS = 4_000L
        private const val SCAN_TIMEOUT_MS = 20_000L
        private const val VERIFY_TIMEOUT_MS = 8_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var currentDevice: BluetoothDevice? = null

    private var scanning = false
    private var payloadSize = DEFAULT_PAYLOAD
    private var expectedDeviceId: String? = null
    private var verified = false

    private val candidates = LinkedHashMap<String, ScanResult>()
    private val tried = mutableSetOf<String>()

    /** Raw bytes of the record being reassembled; decoded as UTF-8 at the newline. */
    private val inbound = java.io.ByteArrayOutputStream()
    private var inboundDropping = false

    private val pending = ArrayDeque<() -> Unit>()
    private var busy = false

    val isReady: Boolean get() = verified && gatt != null && rxChar != null

    fun bluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Drop the OS-level Bluetooth bond for a device.
     *
     * Forgetting a device in-app is not enough on its own: the bond outlives it,
     * so re-scanning the sticker reconnects immediately without the device ever
     * opening a pairing window - which is the gate the whole access-control
     * model rests on.
     *
     * removeBond() is not public API, so this is reflective and may legitimately
     * fail on newer Android. The caller must handle false by sending the
     * operator to system Bluetooth settings rather than pretending it worked.
     */
    fun removeBond(address: String): Boolean {
        if (address.isBlank()) return false
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        if (device.bondState == BluetoothDevice.BOND_NONE) return true
        return runCatching {
            val method = device.javaClass.getMethod("removeBond")
            (method.invoke(device) as? Boolean) == true
        }.onFailure { Log.w(TAG, "removeBond unavailable: ${it.message}") }.getOrDefault(false)
    }

    // ---------------- scanning ----------------

    fun connectToDeviceId(deviceId: String) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            listener.onLog("Bluetooth is off")
            listener.onState(State.IDLE, "bluetooth off")
            return
        }
        if (scanning || gatt != null) return

        expectedDeviceId = deviceId
        verified = false
        inbound.reset()
        inboundDropping = false
        candidates.clear()
        tried.clear()
        scanning = true
        listener.onState(State.SCANNING, "searching")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        main.postDelayed(collectDone, SCAN_COLLECT_MS)
        main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        main.removeCallbacks(collectDone)
        main.removeCallbacks(scanTimeout)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val collectDone = Runnable {
        if (!scanning) return@Runnable
        stopScan()
        if (candidates.isEmpty()) {
            listener.onState(State.IDLE, "device not found")
            return@Runnable
        }
        connectNextCandidate()
    }

    private val scanTimeout = Runnable {
        if (scanning) {
            stopScan()
            listener.onState(State.IDLE, "device not found")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val advertisesService =
                result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
            if (!advertisesService) return
            candidates[device.address] = result
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onLog("Scan failed, error=$errorCode")
            listener.onState(State.IDLE, "scan failed")
        }
    }

    private fun connectNextCandidate() {
        val next = candidates.values
            .filter { it.device != null && it.device.address !in tried }
            .maxByOrNull { it.rssi }
        if (next == null) {
            listener.onState(State.IDLE, "device not found")
            return
        }
        tried.add(next.device.address)
        currentDevice = next.device
        listener.onState(State.CONNECTING, next.device.address)
        gatt = next.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    // ---------------- lifecycle ----------------

    fun disconnect() {
        stopScan()
        main.removeCallbacks(verifyTimeout)
        expectedDeviceId = null
        teardown("disconnected")
    }

    private fun teardown(reason: String) {
        pending.clear()
        busy = false
        verified = false
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        rxChar = null
        txChar = null
        currentDevice = null
        payloadSize = DEFAULT_PAYLOAD
        listener.onState(State.IDLE, reason)
    }

    private fun rejectAndTryNext(reason: String) {
        listener.onLog("Rejecting device: $reason")
        pending.clear()
        busy = false
        verified = false
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        rxChar = null
        txChar = null
        currentDevice = null
        payloadSize = DEFAULT_PAYLOAD
        main.post { connectNextCandidate() }
    }

    private val verifyTimeout = Runnable {
        if (!verified) rejectAndTryNext("no heartbeat within ${VERIFY_TIMEOUT_MS / 1000}s")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener.onState(State.DISCOVERING, "discovering")
                main.post { g.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                main.removeCallbacks(verifyTimeout)
                runCatching { g.close() }
                if (gatt === g) {
                    gatt = null
                    rxChar = null
                    txChar = null
                    verified = false
                    pending.clear()
                    busy = false
                    listener.onState(State.IDLE, if (status == 0) "disconnected" else "link lost")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                rejectAndTryNext("service discovery failed ($status)")
                return
            }
            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                rejectAndTryNext("no Nordic UART Service")
                return
            }
            rxChar = service.getCharacteristic(RX_UUID)
            txChar = service.getCharacteristic(TX_UUID)
            if (rxChar == null || txChar == null) {
                rejectAndTryNext("RX/TX characteristic missing")
                return
            }
            enqueue {
                if (!g.requestMtu(REQUESTED_MTU)) {
                    // Rejected up front; onMtuChanged never fires for this
                    // call, so the queue would otherwise jam here forever -
                    // same failure class writeChunk() already guards against.
                    listener.onLog("requestMtu rejected")
                    operationComplete()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            payloadSize = if (status == BluetoothGatt.GATT_SUCCESS && mtu > ATT_HEADER_BYTES) {
                minOf(mtu - ATT_HEADER_BYTES, MAX_WRITE_BYTES)
            } else {
                DEFAULT_PAYLOAD
            }
            Log.i(TAG, "MTU=$mtu -> write chunk $payloadSize B")
            operationComplete()
            enableNotifications()
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    listener.onState(State.VERIFYING, "confirming identity")
                    main.postDelayed(verifyTimeout, VERIFY_TIMEOUT_MS)
                } else {
                    rejectAndTryNext("could not subscribe ($status)")
                }
            }
            operationComplete()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            // The device no longer demands a bond for this write (RX is plain
            // write, not encrypt-write), so GATT_INSUFFICIENT_AUTHENTICATION/
            // _ENCRYPTION shouldn't occur in normal operation - if it does
            // (e.g. an older/misconfigured unit), it's just logged rather than
            // driving a bonding flow this app no longer implements.
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onLog("Write failed, status=$status")
            }
            operationComplete()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == TX_UUID) consumeInbound(value)
        }

        @Deprecated("Called on API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == TX_UUID
            ) {
                characteristic.value?.let { consumeInbound(it) }
            }
        }
    }

    private fun enableNotifications() {
        val g = gatt ?: return
        val tx = txChar ?: return
        if (!g.setCharacteristicNotification(tx, true)) {
            rejectAndTryNext("setCharacteristicNotification failed")
            return
        }
        val cccd = tx.getDescriptor(CCCD_UUID) ?: run {
            rejectAndTryNext("no CCCD on TX")
            return
        }
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        enqueue {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, enable)
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = enable
                    if (g.writeDescriptor(cccd)) BluetoothGatt.GATT_SUCCESS else -1
                }
            }
            if (result != BluetoothGatt.GATT_SUCCESS) {
                // Rejected up front; onDescriptorWrite never fires for this
                // call, so the queue would otherwise jam here forever - same
                // failure class writeChunk() already guards against.
                listener.onLog("writeDescriptor rejected (result=$result)")
                operationComplete()
            }
        }
    }

    // ---------------- inbound ----------------

    /**
     * Reassemble a record from BLE chunks.
     *
     * Bytes are accumulated and decoded as UTF-8 only once a full line has
     * arrived. Decoding per byte would corrupt every multi-byte character: a
     * chunk boundary can fall in the middle of one, and in Kotlin a Byte is
     * signed, so anything >= 0x80 goes negative and converts to garbage. Task
     * and operator names in this deployment are routinely non-ASCII.
     */
    private fun consumeInbound(chunk: ByteArray) {
        for (b in chunk) {
            when {
                b == NEWLINE -> {
                    val line = String(inbound.toByteArray(), Charsets.UTF_8)
                    inbound.reset()
                    if (!inboundDropping && line.isNotEmpty()) {
                        main.post { handleLine(line) }
                    }
                    inboundDropping = false
                }
                b == CARRIAGE_RETURN -> Unit
                inboundDropping -> Unit
                inbound.size() < MAX_LINE_BYTES -> inbound.write(b.toInt())
                else -> {
                    inboundDropping = true
                    inbound.reset()
                }
            }
        }
    }

    private fun handleLine(line: String) {
        val json = runCatching { JSONObject(line) }.getOrNull()
        if (json == null) {
            Log.w(TAG, "unparseable line: ${line.take(120)}")
            return
        }
        if (!verified) {
            val id = json.optString("device_id")
            if (id.isNotEmpty()) {
                onDeviceIdReceived(id)
                if (!verified) return
            }
        }
        listener.onLine(json)
    }

    private fun onDeviceIdReceived(deviceId: String) {
        main.removeCallbacks(verifyTimeout)
        val want = expectedDeviceId
        if (want != null && !deviceId.equals(want, ignoreCase = true)) {
            rejectAndTryNext("device_id mismatch (found $deviceId, wanted $want)")
            return
        }
        verified = true
        listener.onVerified(deviceId, currentDevice?.address.orEmpty())
        listener.onState(State.READY, "connected")
    }

    // ---------------- outbound ----------------

    /** Send one JSON record; returns false when not connected to a verified device. */
    fun sendJson(json: JSONObject): Boolean {
        val g = gatt
        val rx = rxChar
        if (g == null || rx == null || !verified) return false

        val payload = (json.toString() + "\n").toByteArray(Charsets.UTF_8)
        val chunk = payloadSize.coerceAtLeast(1)
        Log.i(TAG, "sendJson ${payload.size}B in chunks of $chunk")

        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunk, payload.size)
            val slice = payload.copyOfRange(offset, end)
            enqueue { writeChunk(g, rx, slice) }
            offset = end
        }
        return true
    }

    private fun writeChunk(g: BluetoothGatt, rx: BluetoothGattCharacteristic, data: ByteArray) {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(rx, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            run {
                rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                rx.value = data
                if (g.writeCharacteristic(rx)) BluetoothGatt.GATT_SUCCESS else -1
            }
        }
        Log.i(TAG, "writeChunk ${data.size}B -> result=$result")
        if (result != BluetoothGatt.GATT_SUCCESS) {
            // The op never reaches onCharacteristicWrite when it is rejected up
            // front, so release the queue here or every later chunk stalls.
            listener.onLog("write rejected (${data.size}B, result=$result)")
            operationComplete()
        }
    }

    // ---------------- op queue ----------------

    private fun enqueue(op: () -> Unit) {
        main.post {
            pending.addLast(op)
            drain()
        }
    }

    private fun operationComplete() {
        main.post {
            busy = false
            drain()
        }
    }

    private fun drain() {
        if (busy) return
        val op = pending.pollFirst() ?: return
        busy = true
        runCatching { op() }.onFailure {
            // Never swallow this: a thrown write silently drops one chunk and
            // the peer then parses a fragment, which looks like corruption
            // rather than a failed send.
            Log.e(TAG, "GATT operation threw", it)
            listener.onLog("GATT operation failed: ${it.message}")
            busy = false
            drain()
        }
    }
}
