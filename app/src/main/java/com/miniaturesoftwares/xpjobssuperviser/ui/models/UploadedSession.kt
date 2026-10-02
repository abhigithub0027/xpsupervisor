package com.miniaturesoftwares.xpjobssuperviser.ui.models

data class UploadedSession(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val size: String,
    val fileCount: Int,
    val thumbnailResId: Int,
    val videoPath: String? = null,
    val isFailed: Boolean = false,
    val rejectionReason: String? = null,
    val qcStatus: String? = null,
    val annotationStatus: String? = null
)