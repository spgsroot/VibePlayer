package ru.spgsroot.vibeplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_galleries")
data class LibraryGalleryEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val thumbnailPath: String,
    val pagePaths: String,
    val favorite: Boolean,
    val addedAt: Long
)
