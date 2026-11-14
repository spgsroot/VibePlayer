package ru.spgsroot.peachy.ui.gallery

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.domain.model.Video

class GalleryDeletionTest {
    private val video = GalleryItem.VideoItem(Video(1, "video", "/video", 1000, null, 1, 10, false))
    private val photo = GalleryItem.PhotoItem(PhotoEntity(1, "photo", "/photo", null, 2, 10, 100, 100))

    @Test
    fun failedPhotoStaysSelectedAndDoesNotPreventDeletingVideoWithSameId() = runTest {
        var selection = setOf(photo.identity, video.identity)
        val deleted = mutableListOf<GalleryIdentity>()
        val failures = deleteGalleryItems(listOf(photo, video), delete = { item ->
            if (item is GalleryItem.PhotoItem) throw IOException("Cannot delete photo file")
            deleted += item.identity
        }, onDeleted = { selection -= it })
        assertEquals(setOf(photo.identity), failures)
        assertEquals(setOf(photo.identity), selection)
        assertEquals(listOf(video.identity), deleted)
    }

    @Test
    fun successfulMixedDeletionClearsEachTypedSelection() = runTest {
        var selection = setOf(photo.identity, video.identity)
        val deleted = mutableListOf<GalleryIdentity>()
        val failures = deleteGalleryItems(listOf(photo, video), delete = { deleted += it.identity },
            onDeleted = { selection -= it })
        assertTrue(failures.isEmpty())
        assertTrue(selection.isEmpty())
        assertEquals(listOf(photo.identity, video.identity), deleted)
    }

    @Test
    fun cancellationPropagatesWithoutReportingFailureOrDeletingLaterItems() = runTest {
        var attempts = 0
        var successful = 0
        val cancelled = CancellationException("screen disposed")
        try {
            deleteGalleryItems(listOf(photo, video), delete = {
                attempts++
                throw cancelled
            }, onDeleted = { successful++ })
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
        assertEquals(1, attempts)
        assertEquals(0, successful)
    }
}
