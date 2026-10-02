package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.network.UploadedSessionApi
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Sessions across the fleet, on the recorder app's own session card.
 *
 * The card carries per-session actions there (upload, resume, delete). A
 * supervisor is looking at work already uploaded and owns none of those, so the
 * action slots are hidden rather than shown disabled - an inert button invites
 * a tap that will never do anything.
 */
class SessionAdapter : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    private var items: List<UploadedSessionApi> = emptyList()

    fun submit(sessions: List<UploadedSessionApi>) {
        items = sessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
    )

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.tvSessionName)
        private val id: TextView = view.findViewById(R.id.tvSessionId)
        private val date: TextView = view.findViewById(R.id.tvDate)
        private val time: TextView = view.findViewById(R.id.tvTime)
        private val size: TextView = view.findViewById(R.id.tvSize)
        private val action: TextView = view.findViewById(R.id.btnAction)
        private val resume: View = view.findViewById(R.id.btnResume)
        private val play: View = view.findViewById(R.id.ivPlay)
        private val overlay: View = view.findViewById(R.id.uploadOverlay)

        fun bind(session: UploadedSessionApi) {
            val ctx = itemView.context
            name.text = session.taskDetails?.taskTitle
                ?: session.taskId
                ?: ctx.getString(R.string.sessions_untitled)
            id.text = session.id

            val started = session.sessionStartTime ?: session.createdAt
            date.text = formatDate(started) ?: "—"
            time.text = formatTime(started).orEmpty()
            size.text = session.fileSizeBytes?.takeIf { it > 0 }?.let(::formatSize).orEmpty()

            // The QC verdict is the only thing a supervisor can act on here, so
            // it takes the action slot the recorder app uses for upload.
            val status = session.qcStatusXp.orEmpty()
            action.text = status.ifBlank { ctx.getString(R.string.sessions_pending) }
            action.setTextColor(
                when {
                    status.equals("Fail", ignoreCase = true) -> Color.parseColor("#E53935")
                    status.equals("Pass", ignoreCase = true) -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#8A8E9B")
                }
            )
            action.isEnabled = false

            resume.visibility = View.GONE
            overlay.visibility = View.GONE
            // No local file exists on a supervisor's phone, so there is nothing
            // to play inline.
            play.visibility = View.GONE
        }
    }

    private fun parse(iso: String?): java.util.Date? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(iso)
        }.getOrNull()
    }

    private fun formatDate(iso: String?): String? =
        parse(iso)?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
            ?: iso?.take(10)

    private fun formatTime(iso: String?): String? =
        parse(iso)?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else String.format("%.1f MB", mb)
    }
}
