package ru.spgsroot.vibeplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val duration: Long,
    val thumbnailPath: String?,
    val addedAt: Long,
    val fileSize: Long,
    val isCorrupted: Boolean = false
)
