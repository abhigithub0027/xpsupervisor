package com.miniaturesoftwares.xpjobssuperviser.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.cybercap.FleetManager
import com.miniaturesoftwares.xpjobssuperviser.cybercap.UploadSummary

/**
 * The fleet list. One row per cap, rendered from its live state.
 *
 * Every row is driven entirely by the heartbeat, so a device that has gone
 * quiet must visibly stop looking live - stale numbers that read as current
 * are worse than no numbers.
 */
class FleetAdapter(
    private val onAction: (FleetManager.DeviceState) -> Unit,
    private val onLongPress: (FleetManager.DeviceState) -> Unit,
    private val onOpen: (FleetManager.DeviceState) -> Unit = {},
    private val onOpenStorage: (FleetManager.DeviceState) -> Unit = {},
) : RecyclerView.Adapter<FleetAdapter.ViewHolder>() {

    private var items: List<FleetManager.DeviceState> = emptyList()

    fun submit(states: List<FleetManager.DeviceState>) {
        items = states
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_fleet_device, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dot: View = view.findViewById(R.id.vStatusDot)
        private val name: TextView = view.findViewById(R.id.tvDeviceName)
        private val status: TextView = view.findViewById(R.id.tvDeviceStatus)
        private val action: TextView = view.findViewById(R.id.btnAction)
        private val metrics: View = view.findViewById(R.id.layoutMetrics)
        private val storage: TextView = view.findViewById(R.id.tvStorage)
        private val temp: TextView = view.findViewById(R.id.tvTemp)
        private val warning: TextView = view.findViewById(R.id.tvWarning)

        fun bind(state: FleetManager.DeviceState) {
            val ctx = itemView.context
            name.text = state.device.label()

            val hb = state.heartbeat
            val live = state.isConnected && !state.isStale

            status.text = when {
                state.link == FleetManager.LinkState.CONNECTING ->
                    ctx.getString(R.string.fleet_status_connecting)
                state.link == FleetManager.LinkState.WAITING_SLOT ->
                    ctx.getString(R.string.fleet_status_waiting)
                state.isStale && state.link != FleetManager.LinkState.IDLE ->
                    ctx.getString(R.string.fleet_status_stale)
                !state.isConnected -> ctx.getString(R.string.fleet_status_idle)
                hb?.recording == true ->
                    ctx.getString(R.string.fleet_status_recording, formatDuration(hb.sessionSec))
                else -> ctx.getString(R.string.fleet_status_ready)
            }

            dot.setBackgroundColor(
                when {
                    !live -> Color.parseColor("#4A4E69")
                    hb?.recording == true -> Color.parseColor("#FFB300")
                    else -> Color.parseColor("#4CAF50")
                }
            )

            // Metrics are only meaningful while the link is live. Showing the
            // last known free space next to a dead device invites the operator
            // to trust a number that may be hours old.
            if (live && hb != null) {
                metrics.visibility = View.VISIBLE
                val free = hb.freeMb?.let(::formatSize) ?: "—"
                storage.text = ctx.getString(
                    if (hb.sdMounted) R.string.fleet_storage_sd else R.string.fleet_storage_internal,
                    free
                )
                temp.text = hb.socTempC?.let { "$it°C" } ?: "—"
            } else {
                metrics.visibility = View.GONE
            }

            val refusal = state.activeRefusal
            val warn = when {
                // A refusal ranks above any condition warning: it is the device
                // answering the button the supervisor just pressed.
                refusal != null -> ctx.getString(
                    R.string.fleet_refused,
                    refusal.detail.ifBlank { ctx.getString(R.string.fleet_refused_no_reason) }
                )
                !live || hb == null -> null
                !hb.sdMounted -> ctx.getString(R.string.fleet_warn_no_sd)
                (hb.freeMb ?: Long.MAX_VALUE) < LOW_SPACE_MB ->
                    ctx.getString(R.string.fleet_warn_low_space)
                (hb.socTempC ?: 0) >= HOT_TEMP_C ->
                    ctx.getString(R.string.fleet_warn_hot, hb.socTempC)
                else -> null
            }
            warning.text = warn.orEmpty()
            warning.visibility = if (warn == null) View.GONE else View.VISIBLE

            val canStart = state.canStart()
            val canStop = state.canStop()
            action.text = ctx.getString(
                if (canStop) R.string.fleet_action_stop else R.string.fleet_action_start
            )
            action.setBackgroundResource(
                if (canStop) R.drawable.bg_tab_unselected else R.drawable.bg_tab_selected
            )
            action.setTextColor(
                if (canStop) Color.parseColor("#B80765") else Color.WHITE
            )
            action.isEnabled = canStart || canStop
            action.alpha = if (action.isEnabled) 1f else 0.4f
            action.setOnClickListener { if (action.isEnabled) onAction(state) }

            // Tapping the row (not the Start/Stop button) opens the cap's live
            // recording-stats screen.
            itemView.setOnClickListener { onOpen(state) }
            itemView.setOnLongClickListener { onLongPress(state); true }

            bindUploadLine(state)
        }

        /**
         * The upload summary line, and the way into the storage screen.
         *
         * Three cases are kept apart rather than collapsed into "fine": the
         * device has reported no uploader status at all, the snapshot is stale,
         * or the counters are current. Showing the first two as "All uploaded"
         * would claim data is safely off the device when nothing is known.
         */
        private fun bindUploadLine(state: FleetManager.DeviceState) {
            val ctx = itemView.context
            val upload = state.heartbeat?.upload
            val line = itemView.findViewById<android.widget.TextView>(R.id.tvUploadLine)

            if (upload == null) {
                line.text = ctx.getString(R.string.upload_line_unknown)
                line.setTextColor(mutedColor(line))
            } else {
                val backlog = upload.pending + upload.waitingEncrypt
                when {
                    upload.state == UploadSummary.State.UNKNOWN -> {
                        line.text = ctx.getString(R.string.upload_line_unknown)
                        line.setTextColor(mutedColor(line))
                    }
                    upload.state == UploadSummary.State.STALE -> {
                        line.text = ctx.getString(R.string.upload_line_stale)
                        line.setTextColor(Color.parseColor("#E53935"))
                    }
                    backlog > 0 -> {
                        val size = if (upload.pendingMb >= 1024)
                            String.format("%.1f GB", upload.pendingMb / 1024.0)
                        else "${upload.pendingMb} MB"
                        line.text = ctx.getString(R.string.upload_line_backlog, backlog, size)
                        line.setTextColor(Color.parseColor("#B07D00"))
                    }
                    else -> {
                        line.text = ctx.getString(R.string.upload_line_idle)
                        line.setTextColor(Color.parseColor("#2E9E5B"))
                    }
                }
                // A failure is what the supervisor most needs to act on, so it
                // outranks the backlog count for this one line.
                if (upload.failedTotal > 0) {
                    line.text = ctx.getString(R.string.upload_line_failed, upload.failedTotal)
                    line.setTextColor(Color.parseColor("#E53935"))
                }
            }

            line.setOnClickListener { onOpenStorage(state) }
        }

        /** The theme's secondary text colour, so this survives a dark theme. */
        private fun mutedColor(view: android.widget.TextView): Int {
            val attrs = intArrayOf(R.attr.secondaryTextColor)
            val ta = view.context.obtainStyledAttributes(attrs)
            val color = ta.getColor(0, Color.GRAY)
            ta.recycle()
            return color
        }
    }

    private fun formatDuration(totalSec: Int): String =
        String.format("%02d:%02d:%02d", totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)

    private fun formatSize(mb: Long): String =
        if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else "$mb MB"

    private companion object {
        const val LOW_SPACE_MB = 2048L
        const val HOT_TEMP_C = 80
    }
}
