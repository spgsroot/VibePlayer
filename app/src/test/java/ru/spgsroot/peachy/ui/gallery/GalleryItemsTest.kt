package ru.spgsroot.peachy.ui.gallery

import org.junit.Assert.*
import org.junit.Test
import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.domain.model.Video

class GalleryItemsTest {
    private fun video(id: Long, title: String, added: Long) =
        Video(id, title, "/video/$id", 1000, null, added, 10, false)

    private fun photo(id: Long, title: String, added: Long) =
        PhotoEntity(id, title, "/photo/$id", null, added, 10, 100, 200)

    @Test
    fun newestFirstWithDeterministicTypeThenIdTiesRegardlessOfInputOrder() {
        val videos = listOf(video(9, "old", 1), video(3, "tie", 10), video(1, "tie", 10))
        val photos = listOf(photo(3, "tie", 10), photo(1, "tie", 10), photo(7, "new", 20))
        val expected = listOf("photo:7", "video:1", "video:3", "photo:1", "photo:3", "video:9")
        assertEquals(expected, galleryItems(videos, photos, "").map { it.identity.stableKey })
        assertEquals(expected, galleryItems(videos.reversed(), photos.reversed(), "").map { it.identity.stableKey })
    }

    @Test
    fun titleSearchIsCaseInsensitiveForBothTypesAndDoesNotSearchPaths() {
        val videos = listOf(video(1, "SUNset", 2), video(2, "other", 3))
        val photos = listOf(photo(1, "sunrise", 4), photo(2, "other", 5))
        assertEquals(listOf("photo:1", "video:1"), galleryItems(videos, photos, "sUn").map { it.identity.stableKey })
        assertTrue(galleryItems(videos, photos, "/photo").isEmpty())
        assertEquals(4, galleryItems(videos, photos, "").size)
    }

    @Test
    fun matchingNumericIdsRemainDistinctForSelectionAndVideoOnlyActions() {
        val items = galleryItems(listOf(video(1, "video", 1)), listOf(photo(1, "photo", 1)), "")
        val videoId = GalleryIdentity(GalleryMediaType.Video, 1)
        val photoId = GalleryIdentity(GalleryMediaType.Photo, 1)
        val selected = items.map { it.identity }.toSet()
        assertEquals(2, selected.size)
        assertEquals(listOf("photo:1"), items.filter { it.identity in setOf(photoId) }.map { it.identity.stableKey })
        assertTrue(setOf(videoId).allowsVideoActions())
        assertFalse(setOf(photoId).allowsVideoActions())
        assertFalse(selected.allowsVideoActions())
        assertFalse(emptySet<GalleryIdentity>().allowsVideoActions())
    }

    @Test
    fun itemsKeepOriginalTypedModelsRatherThanTurningPhotosIntoVideos() {
        val video = video(1, "movie", 1)
        val photo = photo(1, "image", 2)
        val items = galleryItems(listOf(video), listOf(photo), "")
        assertSame(photo, (items[0] as GalleryItem.PhotoItem).photo)
        assertSame(video, (items[1] as GalleryItem.VideoItem).video)
    }
}
