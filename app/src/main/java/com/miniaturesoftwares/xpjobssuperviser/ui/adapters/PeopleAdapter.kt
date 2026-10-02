package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.StaffMember

class PeopleAdapter(
    private val onClick: (StaffMember) -> Unit = {},
) : RecyclerView.Adapter<PeopleAdapter.ViewHolder>() {

    private var items: List<StaffMember> = emptyList()

    fun submit(staff: List<StaffMember>) {
        items = staff
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
    )

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dot: View = view.findViewById(R.id.vStatusDot)
        private val name: TextView = view.findViewById(R.id.tvName)
        private val detail: TextView = view.findViewById(R.id.tvDetail)
        private val sessions: TextView = view.findViewById(R.id.tvSessions)
        private val status: TextView = view.findViewById(R.id.tvStatus)

        fun bind(person: StaffMember) {
            val ctx = itemView.context
            name.text = person.fullName

            // What they are doing right now, or plainly that they have no cap.
            detail.text = listOfNotNull(
                person.assignedDeviceName ?: person.assignedDeviceId,
                person.currentTaskTitle,
            ).joinToString(" · ").ifBlank { ctx.getString(R.string.people_no_device) }

            sessions.text = ctx.getString(
                R.string.people_sessions, person.sessionsToday, person.sessionsTotal
            )

            val colour = when (person.status) {
                "active" -> "#4CAF50"
                "idle" -> "#FFB300"
                else -> "#4A4E69"
            }
            dot.setBackgroundColor(Color.parseColor(colour))
            status.setTextColor(Color.parseColor(colour))
            status.text = ctx.getString(
                when (person.status) {
                    "active" -> R.string.people_active
                    "idle" -> R.string.people_idle
                    else -> R.string.people_offline
                }
            )

            itemView.setOnClickListener { onClick(person) }
        }
    }
}
