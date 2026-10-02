package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.DeviceRecord

/**
 * The registered device roster.
 *
 * [inRange] comes from the live BLE fleet, not from the backend record - the
 * server's "last seen" can be hours old, and a supervisor standing next to a
 * cap needs to know whether *this phone* can reach it right now.
 */
class DeviceAdapter(
    private val inRange: (String) -> Boolean,
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private var items: List<DeviceRecord> = emptyList()

    fun submit(devices: List<DeviceRecord>) {
        items = devices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
    )

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.tvDeviceName)
        private val link: TextView = view.findViewById(R.id.tvLinkState)
        private val assignee: TextView = view.findViewById(R.id.tvAssignee)
        private val meta: TextView = view.findViewById(R.id.tvDeviceMeta)

        fun bind(device: DeviceRecord) {
            val ctx = itemView.context
            name.text = device.name?.takeIf { it.isNotBlank() } ?: device.deviceId

            val live = inRange(device.deviceId)
            link.text = ctx.getString(
                if (live) R.string.devices_in_range else R.string.devices_out_of_range
            )
            link.setTextColor(Color.parseColor(if (live) "#4CAF50" else "#4A4E69"))

            assignee.text = device.assignedToName
                ?.let { ctx.getString(R.string.devices_assigned_to, it) }
                ?: ctx.getString(R.string.devices_unassigned)

            meta.text = ctx.getString(
                R.string.devices_meta,
                device.deviceKitId.orEmpty().ifBlank { device.deviceId },
                device.totalSessions,
            )
        }
    }
}
