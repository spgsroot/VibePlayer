package ru.spgsroot.peachy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "videos",
    foreignKeys = [ForeignKey(entity = AlbumEntity::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("albumId")])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val duration: Long,
    val thumbnailPath: String?,
    val addedAt: Long,
    val fileSize: Long,
    val isCorrupted: Boolean = false,
    val albumId: Long? = null
)
