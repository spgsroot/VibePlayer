package ru.spgsroot.peachy.ui.player

data class BatchImportProgress(
    val total: Int,
    val completed: Int,
    val currentUrl: String,
    val succeeded: Int,
    val failed: Int,
    val lastError: String? = null
)

data class BatchImportSummary(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val failedUrls: List<String>
)
