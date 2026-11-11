package ru.spgsroot.vibeplayer.manga

data class LibraryGallery(
    val id: Int,
    val title: String,
    val thumbnailPath: String,
    val pagePaths: List<String>,
    val favorite: Boolean
)

class MangaLibraryPolicy {
    fun favorite(current: List<LibraryGallery>, gallery: LibraryGallery): List<LibraryGallery> {
        val saved = current.find { it.id == gallery.id }
        val next = (saved ?: gallery).copy(
            title = gallery.title.ifBlank { saved?.title.orEmpty() },
            thumbnailPath = gallery.thumbnailPath.ifBlank { saved?.thumbnailPath.orEmpty() },
            pagePaths = gallery.pagePaths.ifEmpty { saved?.pagePaths.orEmpty() },
            favorite = true
        )
        return current.filterNot { it.id == gallery.id } + next
    }

    fun unfavorite(current: List<LibraryGallery>, galleryId: Int): List<LibraryGallery> {
        return current.map { item ->
            if (item.id == galleryId) item.copy(favorite = false) else item
        }
    }

    fun remove(current: List<LibraryGallery>, galleryId: Int): List<LibraryGallery> {
        return current.filterNot { it.id == galleryId }
    }

    fun find(current: List<LibraryGallery>, galleryId: Int): LibraryGallery? {
        return current.find { it.id == galleryId }
    }
}
