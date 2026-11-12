package ru.spgsroot.peachy.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaLibraryPolicyTest {
    @Test
    fun favoriteAddsTheGalleryToTheLibrary() {
        val library = MangaLibraryPolicy()
        val next = library.favorite(emptyList(), sample(id = 9))
        assertEquals(1, next.size)
        assertTrue(next.single().favorite)
    }

    @Test
    fun unfavoriteKeepsTheSavedGallery() {
        val saved = sample(id = 9).copy(favorite = true)
        val next = MangaLibraryPolicy().unfavorite(listOf(saved), galleryId = 9)
        assertFalse(next.single().favorite)
    }

    @Test
    fun removingFromLibraryDropsTheGallery() {
        val next = MangaLibraryPolicy().remove(listOf(sample(id = 9)), galleryId = 9)
        assertTrue(next.isEmpty())
        assertNull(MangaLibraryPolicy().find(next, 9))
    }

    private fun sample(id: Int) = LibraryGallery(
        id = id,
        title = "Eat The Rich!",
        thumbnailPath = "galleries/9/thumb.jpg",
        pagePaths = listOf("galleries/9/1.jpg"),
        favorite = false
    )
}
