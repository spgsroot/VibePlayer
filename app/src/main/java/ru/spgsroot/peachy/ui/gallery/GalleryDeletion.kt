package ru.spgsroot.peachy.ui.gallery

import kotlinx.coroutines.CancellationException

/** A failed repository deletion keeps its row/selection; independent items still get deleted. */
internal suspend fun deleteGalleryItems(
    items: List<GalleryItem>,
    delete: suspend (GalleryItem) -> Unit,
    onDeleted: (GalleryIdentity) -> Unit
): Set<GalleryIdentity> {
    val failed = mutableSetOf<GalleryIdentity>()
    for (item in items) {
        try {
            delete(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed += item.identity
            continue
        }
        onDeleted(item.identity)
    }
    return failed
}
