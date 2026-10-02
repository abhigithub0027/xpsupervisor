package com.miniaturesoftwares.xpjobssuperviser.ui.models

import com.google.gson.annotations.SerializedName

data class TaskCategoryOptionsRes(
    @SerializedName("categories") val categories: List<CategoryOption>
)

data class CategoryOption(
    @SerializedName("name") val name: String,
    @SerializedName("category_code") val categoryCode: String,
    @SerializedName("subcategories") val subcategories: List<SubcategoryOption>,
    var isSelected: Boolean = false // Local UI state
)

data class SubcategoryOption(
    @SerializedName("name") val name: String,
    @SerializedName("work_types") val workTypes: List<WorkTypeOption>? = emptyList(),
    var isSelected: Boolean = false // Local UI state
)

data class WorkTypeOption(
    @SerializedName("name") val name: String,
    @SerializedName("task_tags") val taskTags: List<String>
)
