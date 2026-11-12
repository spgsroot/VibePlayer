package ru.spgsroot.peachy.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the app has learned about a gallery the user opened or was shown: enough to draw a card
 * and to learn tastes from it without asking the API again. [tags] is the encoded tag list.
 */
@Entity(tableName = "gallery_memory")
data class GalleryMemoryEntity(
    @PrimaryKey val galleryId: Int,
    val title: String,
    val thumbnailPath: String,
    val numPages: Int,
    val tags: String,
    val uploadedAt: Long?,
    val numFavorites: Int?,
    val seenAt: Long,
    val dismissed: Boolean
)
