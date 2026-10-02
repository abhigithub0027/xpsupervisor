package com.miniaturesoftwares.xpjobssuperviser.ui.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class NotificationMetadata(
    @SerializedName("task_id") val taskId: String? = null,
    @SerializedName("task_code") val taskCode: String? = null,
    @SerializedName("task_title") val taskTitle: String? = null,
    @SerializedName("video_id") val videoId: String? = null,
    @SerializedName("video_url") val videoUrl: String? = null,
    @SerializedName("category") val category: String? = null
)

data class NotificationItem(
    @SerializedName("_id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("body") val description: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("read") val isRead: Boolean = false,
    @SerializedName("metadata") val metadata: NotificationMetadata? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
) {
    /** Derived: opposite of `read` for UI convenience */
    val isUnread: Boolean get() = !isRead

    /** Compute a human-readable "time ago" string from createdAt */
    val timeAgo: String
        get() {
            if (createdAt.isNullOrEmpty()) return ""
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                val date = sdf.parse(createdAt) ?: return ""
                val diff = System.currentTimeMillis() - date.time

                when {
                    diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                    diff < TimeUnit.HOURS.toMillis(1) -> {
                        val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                        "$mins min ago"
                    }
                    diff < TimeUnit.DAYS.toMillis(1) -> {
                        val hours = TimeUnit.MILLISECONDS.toHours(diff)
                        "$hours hour ago"
                    }
                    diff < TimeUnit.DAYS.toMillis(7) -> {
                        val days = TimeUnit.MILLISECONDS.toDays(diff)
                        "$days day ago"
                    }
                    else -> {
                        SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)
                    }
                }
            } catch (e: Exception) {
                ""
            }
        }
}