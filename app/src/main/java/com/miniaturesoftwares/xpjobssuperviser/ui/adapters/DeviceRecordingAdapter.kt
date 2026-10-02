package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.cybercap.DeviceRecording
import com.miniaturesoftwares.xpjobssuperviser.cybercap.SegmentState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The list of recordings held on one cap.
 *
 * A [ListAdapter] rather than a plain adapter because pages arrive one at a
 * time and the list is re-submitted on every refresh; without diffing, the
 * whole list would flash on each heartbeat-driven update.
 */
class DeviceRecordingAdapter :
    ListAdapter<DeviceRecording, DeviceRecordingAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.vStateDot)
        val name: TextView = view.findViewById(R.id.tvName)
        val state: TextView = view.findViewById(R.id.tvState)
        val meta: TextView = view.findViewById(R.id.tvMeta)
        val task: TextView = view.findViewById(R.id.tvTask)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_recording, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        // Title is when it was recorded, not the filename. The filename is a
        // 60-character device id and epoch that identifies nothing a supervisor
        // can act on; the time is how they actually remember a session. The
        // name is kept below, small, because support still needs it.
        val stamp = if (item.startTimeUnix > 0) item.startTimeUnix else item.mtimeUnix
        holder.name.text = segmentLabel(item)
            ?: if (stamp > 0) TITLE_FMT.format(Date(stamp * 1000L))
            else ctx.getString(R.string.rec_untitled)

        holder.state.text = ctx.getString(stateLabel(item.state))
        holder.state.setTextColor(stateColor(item.state))
        holder.dot.setBackgroundResource(stateDot(item.state))

        val parts = mutableListOf(formatSize(item.sizeBytes))
        if (item.durationSeconds > 0) {
            // A duration the device could only estimate is prefixed with "~".
            // The exact value comes from the segment's sidecar, which the
            // uploader deletes once the backend has been told about the file -
            // so older segments legitimately have only an approximation, and
            // showing it as exact would misrepresent it.
            val d = formatDuration(item.durationSeconds)
            parts += if (item.durationExact) d else "~$d"
        }
        // How long it has been sitting here. This is what tells a slow link
        // apart from a recording that is never going to leave.
        if (item.state != SegmentState.RECORDING && stamp > 0) {
            val waited = (System.currentTimeMillis() / 1000L) - stamp
            if (waited > 3600) parts += ctx.getString(R.string.rec_waiting_fmt, formatAge(waited))
        }
        holder.meta.text = parts.joinToString(" · ")

        val detail = buildList {
            item.taskId?.let { add(ctx.getString(R.string.task_prefix_fmt, it)) }
            item.segmentIndex?.let { add(ctx.getString(R.string.segment_prefix_fmt, it)) }
            add(item.name)
        }
        holder.task.visibility = View.VISIBLE
        holder.task.text = detail.joinToString(" · ")
    }

    /**
     * The segment's own time, taken from the trailing `_YYYYMMDD_HHMMSS` in its
     * filename.
     *
     * Preferred over the epoch earlier in the name because that epoch
     * identifies the recording *session*: every segment of one session carries
     * the same value, so a list of them all showed an identical timestamp and
     * read as a bug.
     *
     * Shown exactly as the device wrote it, with no timezone conversion. It is
     * the device's own label for the file, and the device's timezone is not
     * something we know - converting it would silently shift the time away from
     * what the filename underneath plainly says.
     */
    private fun segmentLabel(item: DeviceRecording): String? {
        val m = SEGMENT_STAMP.find(item.name) ?: return null
        val (y, mo, d, h, mi) = m.destructured
        val month = MONTHS.getOrNull(mo.toInt() - 1) ?: return null
        return "${d.trimStart('0')} $month $y, $h:$mi"
    }

    private fun formatAge(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }

    private fun stateLabel(state: SegmentState): Int = when (state) {
        SegmentState.RECORDING -> R.string.seg_state_recording
        SegmentState.AWAITING_ENCRYPT -> R.string.seg_state_awaiting_encrypt
        SegmentState.ENCRYPTING -> R.string.seg_state_encrypting
        SegmentState.PENDING_UPLOAD -> R.string.seg_state_pending_upload
        SegmentState.UPLOADING -> R.string.seg_state_uploading
        SegmentState.UNKNOWN -> R.string.seg_state_unknown
    }

    private fun stateColor(state: SegmentState): Int = when (state) {
        SegmentState.RECORDING, SegmentState.UPLOADING -> COLOR_ACTIVE
        SegmentState.UNKNOWN -> COLOR_BAD
        else -> COLOR_WAITING
    }

    private fun stateDot(state: SegmentState): Int = when (state) {
        SegmentState.RECORDING, SegmentState.UPLOADING -> R.drawable.bg_circle_green
        SegmentState.UNKNOWN -> R.drawable.bg_circle_red
        else -> R.drawable.bg_circle_yellow
    }

    companion object {
        private const val COLOR_ACTIVE = 0xFF2E9E5B.toInt()
        private const val COLOR_WAITING = 0xFFB07D00.toInt()
        private const val COLOR_BAD = 0xFFE53935.toInt()

        private val TITLE_FMT = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

        /** Trailing `_YYYYMMDD_HHMMSS` that video_recording appends per segment. */
        private val SEGMENT_STAMP =
            Regex("""_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})\d{2}$""")
        private val MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )

        private val DIFF = object : DiffUtil.ItemCallback<DeviceRecording>() {
            override fun areItemsTheSame(a: DeviceRecording, b: DeviceRecording) = a.name == b.name
            override fun areContentsTheSame(a: DeviceRecording, b: DeviceRecording) = a == b
        }

        fun formatSize(bytes: Long): String = when {
            bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

        fun formatDuration(seconds: Double): String {
            val total = seconds.toLong()
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
