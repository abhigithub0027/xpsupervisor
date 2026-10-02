package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.ui.models.AvailableTask

class TaskAdapter(
    private val onClick: (AvailableTask) -> Unit = {},
) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    private var items: List<AvailableTask> = emptyList()

    fun submit(tasks: List<AvailableTask>) {
        items = tasks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
    )

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val category: TextView = view.findViewById(R.id.tvCategory)
        private val code: TextView = view.findViewById(R.id.tvTaskCode)
        private val title: TextView = view.findViewById(R.id.tvTaskTitle)
        private val meta: TextView = view.findViewById(R.id.tvTaskMeta)

        fun bind(task: AvailableTask) {
            category.text = task.category.ifBlank { "—" }
            code.text = task.masterTaskCode.orEmpty()
            title.text = task.title
            meta.text = listOfNotNull(
                task.duration.takeIf { it.isNotBlank() },
                task.priority.takeIf { it.isNotBlank() }?.let { "Priority: $it" },
                task.basePayInr.takeIf { it > 0 }?.let { "₹$it" },
            ).joinToString(" · ")
            itemView.setOnClickListener { onClick(task) }
        }
    }
}
