package ru.spgsroot.peachy.domain.model

data class Video(
    val id: Long,
    val title: String,
    val filePath: String,
    val duration: Long,
    val thumbnailPath: String?,
    val addedAt: Long,
    val fileSize: Long,
    val isCorrupted: Boolean = false,
    val albumId: Long? = null
)
