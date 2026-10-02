package com.miniaturesoftwares.xpjobssuperviser.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.miniaturesoftwares.xpjobssuperviser.R
import com.miniaturesoftwares.xpjobssuperviser.network.TaskMapping

/** Home recommended-task card. Mirrors the recorder app's design. */
class RecommendedTaskAdapter(
    private val onClick: (TaskMapping) -> Unit
) : ListAdapter<TaskMapping, RecommendedTaskAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommended_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivTaskImage: ImageView = itemView.findViewById(R.id.ivTaskImage)
        private val tvTaskTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        private val btnStartTask: View = itemView.findViewById(R.id.btnStartTask)

        fun bind(item: TaskMapping, onClick: (TaskMapping) -> Unit) {
            tvTaskTitle.text = item.task.taskTitle

            if (!item.task.sampleThumbnailUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(item.task.sampleThumbnailUrl)
                    .transform(CenterCrop(), RoundedCorners(16))
                    .placeholder(R.drawable.bg_stats_card)
                    .into(ivTaskImage)
            }

            val task = item.task
            val submissionCount = task.current_submission_count ?: item.submissionCount ?: 0
            val remainingSubmissions = if (task.max_submissions != null && task.current_submission_count != null) {
                task.max_submissions - task.current_submission_count
            } else {
                item.remainingSubmissions ?: 0
            }
            val maxSessionCount = submissionCount + remainingSubmissions
            val hasProgress = (task.current_submission_count != null && task.max_submissions != null) ||
                (item.submissionCount != null && item.remainingSubmissions != null)
            val isMaxReached = hasProgress && submissionCount >= maxSessionCount

            btnStartTask.visibility = if (isMaxReached) View.GONE else View.VISIBLE

            btnStartTask.setOnClickListener { onClick(item) }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TaskMapping>() {
            override fun areItemsTheSame(old: TaskMapping, new: TaskMapping) = old.mappingId == new.mappingId
            override fun areContentsTheSame(old: TaskMapping, new: TaskMapping) = old == new
        }
    }
}
