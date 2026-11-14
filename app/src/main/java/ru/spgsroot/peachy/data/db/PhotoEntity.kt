package ru.spgsroot.peachy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An imported still image. A separate table deliberately keeps photos out of video playlists,
 * playback state, haptic/audio sync, and video compression; none of those features apply to stills.
 * Dimensions describe the saved image's encoded pixels; the viewer applies its original EXIF.
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val thumbnailPath: String?,
    val addedAt: Long,
    val fileSize: Long,
    val width: Int,
    val height: Int
)
