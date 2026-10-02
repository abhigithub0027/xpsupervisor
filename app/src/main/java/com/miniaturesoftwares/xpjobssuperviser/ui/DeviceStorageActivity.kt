package com.miniaturesoftwares.xpjobssuperviser.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.cybercap.DeviceRecording
import com.miniaturesoftwares.xpjobssuperviser.cybercap.FleetManager
import com.miniaturesoftwares.xpjobssuperviser.cybercap.RecordingsPage
import com.miniaturesoftwares.xpjobssuperviser.cybercap.SegmentState
import com.miniaturesoftwares.xpjobssuperviser.cybercap.UploadProgress
import com.miniaturesoftwares.xpjobssuperviser.cybercap.UploadSummary
import com.miniaturesoftwares.xpjobssuperviser.databinding.ActivityDeviceStorageBinding
import com.miniaturesoftwares.xpjobssuperviser.ui.adapters.DeviceRecordingAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/**
 * Upload progress and the recording inventory for a single cap.
 *
 * Everything here comes over BLE from the device itself, not from the backend:
 * the point of the screen is to answer "what is still sitting on this cap and
 * why has it not arrived", which the backend by definition cannot say.
 *
 * Two different update paths feed it, deliberately:
 *
 *  - The compact upload summary rides the heartbeat, so the numbers at the top
 *    stay live with no polling.
 *  - The per-file inventory is requested explicitly and arrives in pages,
 *    because it is far too large to put on a heartbeat.
 */
@AndroidEntryPoint
class DeviceStorageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceStorageBinding
    private lateinit var adapter: DeviceRecordingAdapter

    @Inject
    lateinit var fleet: FleetManager

    private lateinit var deviceId: String
    private var deviceLabel: String = ""

    /** Accumulated across pages; the device sends newest first. */
    private val loaded = mutableListOf<DeviceRecording>()
    private var totalOnDevice = 0
    private var awaitingPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceStorageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        deviceLabel = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty()
        if (deviceId.isBlank()) {
            // Nothing sensible to show without knowing which cap this is.
            finish()
            return
        }

        adapter = DeviceRecordingAdapter()
        binding.rvRecordings.layoutManager = LinearLayoutManager(this)
        binding.rvRecordings.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { reload() }
        binding.btnLoadMore.setOnClickListener { requestNextPage() }

        binding.tvHeaderTitle.text = deviceLabel.ifBlank { deviceId }

        observeHeartbeat()
        reload()
    }

    override fun onStart() {
        super.onStart()
        // Pages are delivered by a single callback on the shared manager, so
        // it is claimed while this screen is in front and released in onStop.
        // Leaving it installed would keep this Activity referenced after it
        // has gone, and deliver pages to a dead view.
        fleet.onRecordings = { id, page -> runOnUiThread { onPage(id, page) } }
    }

    override fun onStop() {
        super.onStop()
        fleet.onRecordings = null
    }

    /** Keeps the summary card live off the heartbeat. */
    private fun observeHeartbeat() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fleet.fleet
                    .map { list -> list.firstOrNull { it.device.deviceId == deviceId } }
                    .collect { state ->
                        val connected = state?.isConnected == true && state.isStale != true
                        // Only the link state: the device name is already
                        // the title directly above this line.
                        binding.tvHeaderSubtitle.text =
                            if (connected) getString(R.string.upload_state_live_link)
                            else getString(R.string.upload_state_offline)

                        renderUpload(state?.heartbeat?.upload, connected)
                    }
            }
        }
    }

    /**
     * The summary card.
     *
     * Leads with a sentence, not a number, because the question a supervisor is
     * actually asking is "is this cap's data safely off it yet" - and a row of
     * figures makes them derive that answer themselves every time.
     *
     * The figures underneath are the ones that size the problem: how many
     * recordings are still here, how much that is, and how long the oldest has
     * been waiting. The uploader's own totals are real but run-scoped, so they
     * sit on a separate line that says so rather than posing as file counts.
     */
    private fun renderUpload(summary: UploadSummary?, connected: Boolean) {
        // An absent summary is not the same as a healthy one. Firmware that
        // predates upload reporting, and a device that has simply not spoken
        // yet, both land here - and rendering zeroes would claim everything is
        // uploaded when nothing is known.
        if (summary == null || summary.state == UploadSummary.State.UNKNOWN) {
            binding.cardUpload.uploadCardRoot.visibility = View.GONE
            binding.tvUploaderUnknown.visibility = View.VISIBLE
            return
        }
        binding.cardUpload.uploadCardRoot.visibility = View.VISIBLE
        binding.tvUploaderUnknown.visibility = View.GONE

        val card = binding.cardUpload
        val backlog = summary.pending + summary.waitingEncrypt
        val sizeText = formatMb(summary.pendingMb)

        // "Stuck" is claimed only when the device is both carrying a backlog
        // and reporting failures. A slow link alone is not stuck, and saying so
        // would train people to ignore the warning.
        val stuck = backlog > 0 && summary.failedTotal > 0

        val (head, sub, dot) = when {
            summary.state == UploadSummary.State.STALE -> Triple(
                getString(R.string.upload_head_stale),
                getString(R.string.upload_sub_stale, formatAge(summary.ageSec ?: 0)),
                R.drawable.bg_circle_red,
            )
            stuck -> Triple(
                getString(R.string.upload_head_stuck),
                getString(R.string.upload_sub_stuck, sizeText),
                R.drawable.bg_circle_red,
            )
            summary.busy || summary.progress != null -> Triple(
                getString(R.string.upload_head_uploading),
                getString(R.string.upload_sub_progress, sizeText),
                R.drawable.bg_circle_green,
            )
            backlog > 0 -> Triple(
                getString(R.string.upload_head_waiting),
                getString(R.string.upload_sub_uploading, sizeText),
                R.drawable.bg_circle_yellow,
            )
            else -> Triple(
                getString(R.string.upload_head_clear),
                getString(R.string.upload_sub_clear),
                R.drawable.bg_circle_green,
            )
        }
        card.tvUploadState.text = head
        card.tvUploadDetail.text = sub
        card.vUploadDot.setBackgroundResource(dot)

        // How fresh this picture is. Only worth showing once it is old enough
        // to matter - "2s ago" on every frame is noise.
        val age = summary.ageSec
        card.tvUploadAge.text =
            if (age != null && age >= 60) getString(R.string.ago_fmt, formatAge(age)) else ""

        card.tvStatUploaded.text = backlog.toString()
        card.tvStatPending.text = if (backlog > 0) sizeText else getString(R.string.stat_none)
        card.tvStatFailed.text = oldestPendingText(summary)

        card.tvRunCounters.text = if (summary.failedTotal > 0) {
            getString(R.string.run_counters_fmt, summary.uploadedTotal, summary.failedTotal)
        } else {
            getString(R.string.run_counters_no_fail, summary.uploadedTotal)
        }

        renderProgress(summary.progress)

        val err = summary.lastError
        if (err.isNullOrBlank()) {
            card.tvUploadError.visibility = View.GONE
        } else {
            card.tvUploadError.visibility = View.VISIBLE
            card.tvUploadError.text = getString(R.string.last_error_label) + "\n" + err
        }
    }

    /**
     * The live byte counter for the file going out.
     *
     * Driven by the heartbeat, which the device now refreshes every beat while
     * a transfer is in flight, so this advances on its own - nobody has to pull
     * to refresh to watch an upload move.
     *
     * Hidden outright when nothing is uploading. A bar parked at 0% reads as a
     * stall, which is the opposite of what it is there to show.
     */
    private fun renderProgress(p: UploadProgress?) {
        val card = binding.cardUpload
        if (p == null || p.totalBytes <= 0) {
            card.groupProgress.visibility = View.GONE
            return
        }
        card.groupProgress.visibility = View.VISIBLE

        val sent = DeviceRecordingAdapter.formatSize(p.sentBytes)
        val total = DeviceRecordingAdapter.formatSize(p.totalBytes)
        card.tvProgressBytes.text =
            getString(R.string.progress_bytes_fmt, sent, total, p.percent)

        // setProgress(animate) so the bar slides between the once-a-second
        // samples instead of stepping, which reads as a stuttering transfer.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            card.pbUpload.setProgress(p.percent, true)
        } else {
            card.pbUpload.progress = p.percent
        }

        val left = DeviceRecordingAdapter.formatSize(
            (p.totalBytes - p.sentBytes).coerceAtLeast(0L)
        )
        val rate = DeviceRecordingAdapter.formatSize(p.bytesPerSecond)
        val eta = p.etaSeconds
        card.tvProgressRate.text = if (eta != null && eta > 0) {
            getString(R.string.progress_rate_eta_fmt, rate, left, formatAge(eta))
        } else {
            getString(R.string.progress_rate_fmt, rate, left)
        }
    }

    /**
     * Age of the oldest recording still waiting.
     *
     * This is the figure that separates "busy" from "broken": a backlog that is
     * minutes old is a slow link, one that is days old is not moving at all.
     * The heartbeat carries no oldest-pending timestamp, so it is taken from
     * the inventory once a page has loaded, and shows "–" until then rather
     * than a zero that would read as "no wait".
     */
    private fun oldestPendingText(summary: UploadSummary): String {
        if (summary.pending + summary.waitingEncrypt == 0) return getString(R.string.stat_none)
        val oldest = loaded
            .filter { it.state != SegmentState.RECORDING }
            .minByOrNull { if (it.startTimeUnix > 0) it.startTimeUnix else it.mtimeUnix }
            ?: return getString(R.string.stat_none)
        val stamp = if (oldest.startTimeUnix > 0) oldest.startTimeUnix else oldest.mtimeUnix
        if (stamp <= 0) return getString(R.string.stat_none)
        val secs = (System.currentTimeMillis() / 1000L) - stamp
        return if (secs > 0) formatAge(secs) else getString(R.string.stat_none)
    }

    private fun reload() {
        loaded.clear()
        totalOnDevice = 0
        adapter.submitList(emptyList())
        binding.btnLoadMore.visibility = View.GONE
        requestPage(0)
    }

    private fun requestNextPage() = requestPage(loaded.size)

    private fun requestPage(offset: Int) {
        if (awaitingPage) return
        awaitingPage = true
        binding.progress.visibility = if (offset == 0) View.VISIBLE else View.GONE

        if (!fleet.requestRecordings(deviceId, offset)) {
            // The request could not even be written, which means the link is
            // down - distinct from the device answering with an empty list.
            awaitingPage = false
            binding.progress.visibility = View.GONE
            binding.tvEmpty.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
            binding.tvEmpty.text = getString(R.string.device_not_connected)
            toast(getString(R.string.device_not_connected))
        }
    }

    private fun onPage(id: String, page: RecordingsPage) {
        val state = fleet.fleet.value.firstOrNull { it.device.deviceId == deviceId }
        // Pages from another cap can arrive if the supervisor switched screens
        // while one was in flight.
        if (id != deviceId) return

        awaitingPage = false
        binding.progress.visibility = View.GONE

        if (page.offset == 0) loaded.clear()
        loaded.addAll(page.items)
        totalOnDevice = page.total
        adapter.submitList(loaded.toList())

        binding.tvEmpty.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.text = if (page.dirOk) {
            getString(R.string.no_recordings_on_device)
        } else {
            page.dirError ?: getString(R.string.no_recordings_on_device)
        }

        binding.btnLoadMore.visibility =
            if (loaded.size < totalOnDevice) View.VISIBLE else View.GONE

        binding.tvRecordingsSummary.text = getString(
            R.string.recordings_summary_fmt,
            DeviceRecordingAdapter.formatSize(page.bytesOnDisk),
            DeviceRecordingAdapter.formatSize(page.freeBytes),
        )

        // The oldest-waiting figure is derived from the inventory, so it can
        // only be filled in once a page has arrived.
        state?.heartbeat?.upload?.let { renderUpload(it, true) }
    }

    private fun formatAge(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }

    private fun formatMb(mb: Long): String =
        if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else "$mb MB"

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_DEVICE_NAME = "device_name"

        fun intent(context: Context, deviceId: String, deviceName: String): Intent =
            Intent(context, DeviceStorageActivity::class.java)
                .putExtra(EXTRA_DEVICE_ID, deviceId)
                .putExtra(EXTRA_DEVICE_NAME, deviceName)
    }
}
