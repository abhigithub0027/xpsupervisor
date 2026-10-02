package com.miniaturesoftwares.xpjobssuperviser.ui.models

import com.google.gson.annotations.SerializedName

data class TaskCategory(
    @SerializedName("name") val name: String,
    @SerializedName("task_logo") val taskLogo: String,
    var isSelected: Boolean = false
)