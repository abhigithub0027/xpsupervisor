package com.miniaturesoftwares.xpjobssuperviser.ui.models

import android.util.Log
import com.miniaturesoftwares.xpjobssuperviser.network.TaskMapping

data class AvailableTask(
    val taskId: String,
    val category: String,
    val title: String,
    val duration: String,
    val tags: List<String>,
    val priority: String, // "Urgent" or "Low" — mapped from difficulty_level
    val thumbnailUrl: String?, // Now a URL string instead of drawable res
    val basePayInr: Int,
    val assignmentStatus: String?, // "Recommended", "Approved", etc.
    val completedAt: String? = null,
    val submissionCount: Int? = null,
    val remainingSubmissions: Int? = null,
    val updatedAt: String? = null,
    val environmentId: String? = null,
    val environmentLabel: String? = null,
    val masterTaskCode: String? = null
) {
    companion object {
        /**
         * Maps an API TaskMapping to the UI AvailableTask model.
         */
        fun fromTaskMapping(mapping: TaskMapping): AvailableTask {
            val task = mapping.task

            // Prefer the dedicated priority_level field from the API.
            // Fall back to mapping difficulty_level only if priority_level is absent.
            val priority = when {
                !task.priorityLevel.isNullOrBlank() -> task.priorityLevel
                else -> when (task.difficultyLevel?.lowercase()) {
                    "easy" -> "Low"
                    "medium" -> "Medium"
                    "hard" -> "Urgent"
                    else -> task.difficultyLevel ?: "Low"
                }
            }

//            Log.e("4564","EId${task.environmentId}")
//            Log.e("4564","ELabel${task.environmentLabel}")
//            Log.e("4564","masterTaskCode${task.masterTaskCode}")

            return AvailableTask(
                taskId = task.id,
                category = task.category ?: "General",
                title = task.taskTitle ?: "Unnamed Task",
                duration = "${task.durationMinutes ?: 10} min",
                tags = task.taskTags ?: emptyList(),
                priority = priority,
                thumbnailUrl = task.sampleThumbnailUrl,
                basePayInr = task.basePayInr ?: 0,
                assignmentStatus = mapping.assignmentStatus,
                completedAt = mapping.completedAt,
                submissionCount = task.current_submission_count ?: mapping.submissionCount,
                remainingSubmissions = if (task.max_submissions != null && task.current_submission_count != null) {
                    task.max_submissions - task.current_submission_count
                } else {
                    mapping.remainingSubmissions
                },
                updatedAt = mapping.updatedAt ?: task.updatedAt,
                environmentId = task.environmentId,
                environmentLabel = task.environmentLabel,
                masterTaskCode = task.masterTaskCode
            )
        }
    }
}