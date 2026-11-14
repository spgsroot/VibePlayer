package ru.spgsroot.peachy.ui.gallery

/** Bounds are inclusive byte counts; null leaves that end unbounded. */
data class MediaFilter(
    val sort: MediaSort = MediaSort.Newest,
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val type: GalleryMediaType? = null
)
enum class MediaSort { Newest, NameAscending, NameDescending, SizeAscending, SizeDescending }

fun filterGalleryItems(items: List<GalleryItem>, filter: MediaFilter): List<GalleryItem> {
    val primary = when (filter.sort) {
        MediaSort.Newest -> compareByDescending<GalleryItem> { it.addedAt }
        MediaSort.NameAscending -> Comparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) }
        MediaSort.NameDescending -> Comparator { a, b -> b.title.compareTo(a.title, ignoreCase = true) }
        MediaSort.SizeAscending -> compareBy { it.fileSize }
        MediaSort.SizeDescending -> compareByDescending { it.fileSize }
    }
    return items.filter {
        (filter.type == null || it.identity.type == filter.type) &&
            (filter.minBytes == null || it.fileSize >= filter.minBytes) &&
            (filter.maxBytes == null || it.fileSize <= filter.maxBytes)
    }.sortedWith(primary.thenBy { it.identity.type.ordinal }.thenBy { it.identity.id })
}
