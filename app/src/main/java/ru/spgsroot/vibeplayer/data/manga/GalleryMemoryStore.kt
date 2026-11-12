package ru.spgsroot.vibeplayer.data.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.spgsroot.vibeplayer.data.db.GalleryMemoryDao
import ru.spgsroot.vibeplayer.data.db.GalleryMemoryEntity
import ru.spgsroot.vibeplayer.manga.GalleryDetail
import ru.spgsroot.vibeplayer.manga.GallerySummary
import ru.spgsroot.vibeplayer.manga.RememberedGallery
import ru.spgsroot.vibeplayer.manga.TagCodec
import ru.spgsroot.vibeplayer.manga.toRemembered
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryMemoryStore @Inject constructor(private val dao: GalleryMemoryDao) {
    val all: Flow<List<RememberedGallery>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Upload dates of every gallery whose page has been opened; search results do not carry one. */
    val uploadDates: Flow<Map<Int, Long>> = all.map { galleries ->
        buildMap { galleries.forEach { gallery -> gallery.uploadedAt?.let { put(gallery.id, it) } } }
    }

    /** Opening a gallery shows interest, so it also lifts an earlier "not interested". */
    suspend fun remember(detail: GalleryDetail, now: Long = System.currentTimeMillis()) {
        dao.upsert(detail.toRemembered(now).toEntity())
    }

    /** Hides [gallery] from recommendations for good and teaches the profile to avoid its tags. */
    suspend fun dismiss(gallery: GallerySummary, now: Long = System.currentTimeMillis()) {
        val known = dao.getById(gallery.id)
        dao.upsert((known ?: gallery.toRemembered(now).toEntity()).copy(dismissed = true))
    }
}

private fun GalleryMemoryEntity.toDomain() = RememberedGallery(
    id = galleryId,
    title = title,
    thumbnailPath = thumbnailPath,
    numPages = numPages,
    tags = TagCodec.decode(tags),
    uploadedAt = uploadedAt,
    numFavorites = numFavorites,
    seenAt = seenAt,
    dismissed = dismissed
)

private fun RememberedGallery.toEntity() = GalleryMemoryEntity(
    galleryId = id,
    title = title,
    thumbnailPath = thumbnailPath,
    numPages = numPages,
    tags = TagCodec.encode(tags),
    uploadedAt = uploadedAt,
    numFavorites = numFavorites,
    seenAt = seenAt,
    dismissed = dismissed
)
