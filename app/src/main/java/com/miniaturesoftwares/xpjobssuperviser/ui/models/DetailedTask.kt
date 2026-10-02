package com.miniaturesoftwares.xpjobssuperviser.ui.models

data class DetailedTask(
    val taskId: String,
    val taskCode: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val tags: List<String>,
    val duration: String,
    val basePay: String,
    val difficulty: String,
    val thumbnailUrl: String?,
    val instructions: List<String>,
    val requirements: List<String>,
    val mistakes: List<String>,
    val brief: String? = null,
    val environmentId: String? = null,
    val environmentLabel: String? = null,
    val masterTaskCode: String? = null,
    val priority: String,
    /** Raw backend metadata template, as JSON. See TaskDetail.metadataTemplate. */
    val metadataTemplate: String? = null
)