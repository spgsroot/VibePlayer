package ru.spgsroot.vibeplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_history")
data class ReadHistoryEntity(
    @PrimaryKey val galleryId: Int,
    val title: String,
    val thumbnailPath: String,
    val page: Int,
    val pageCount: Int,
    val readAt: Long
)
