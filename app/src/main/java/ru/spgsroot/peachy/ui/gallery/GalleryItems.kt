package ru.spgsroot.peachy.ui.gallery

import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.domain.model.Video

/** Declaration order is the tie breaker for equal addedAt values: videos, then photos. */
enum class GalleryMediaType { Video, Photo }

data class GalleryIdentity(val type: GalleryMediaType, val id: Long) {
    // A String is also a saveable LazyGrid key; raw database ids can collide across tables.
    val stableKey: String get() = "${if (type == GalleryMediaType.Video) "video" else "photo"}:$id"
}

sealed interface GalleryItem {
    val identity: GalleryIdentity
    val title: String
    val addedAt: Long
    val fileSize: Long
    val albumId: Long?

    data class VideoItem(val video: Video) : GalleryItem {
        override val identity = GalleryIdentity(GalleryMediaType.Video, video.id)
        override val title get() = video.title
        override val addedAt get() = video.addedAt
        override val fileSize get() = video.fileSize
        override val albumId get() = video.albumId
    }

    data class PhotoItem(val photo: PhotoEntity) : GalleryItem {
        override val identity = GalleryIdentity(GalleryMediaType.Photo, photo.id)
        override val title get() = photo.title
        override val addedAt get() = photo.addedAt
        override val fileSize get() = photo.fileSize
        override val albumId get() = photo.albumId
    }
}

/** Titles only; repository emission order cannot affect equal-timestamp ordering. */
fun galleryItems(videos: List<Video>, photos: List<PhotoEntity>, query: String, filter: MediaFilter = MediaFilter()): List<GalleryItem> =
    filterGalleryItems(
        (videos.map { GalleryItem.VideoItem(it) } + photos.map { GalleryItem.PhotoItem(it) })
            .filter { it.title.contains(query, ignoreCase = true) },
        filter
    )

fun Set<GalleryIdentity>.allowsVideoActions(): Boolean =
    isNotEmpty() && all { it.type == GalleryMediaType.Video }
