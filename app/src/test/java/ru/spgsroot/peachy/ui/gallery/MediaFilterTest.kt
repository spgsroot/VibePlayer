package ru.spgsroot.peachy.ui.gallery

import org.junit.Assert.*
import org.junit.Test
import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.domain.model.Video

class MediaFilterTest {
    private val mixed = galleryItems(
        listOf(Video(1, "zebra", "v1", 10, null, 1, 20, false), Video(2, "Apple", "v2", 10, null, 2, 30, false)),
        listOf(PhotoEntity(1, "apple", "p1", null, 3, 10, 1, 1)), ""
    )
    @Test fun inclusiveByteBoundsAndTypeAreCombined() {
        assertEquals(listOf("video:1"), filterGalleryItems(mixed, MediaFilter(minBytes = 10, maxBytes = 20, type = GalleryMediaType.Video)).map { it.identity.stableKey })
        assertEquals(listOf("photo:1"), filterGalleryItems(mixed, MediaFilter(maxBytes = 10)).map { it.identity.stableKey })
    }
    @Test fun namesIgnoreCaseAndEqualNamesHaveStableKeys() {
        val expected = listOf("video:2", "photo:1", "video:1")
        val options = MediaFilter(sort = MediaSort.NameAscending)
        assertEquals(expected, filterGalleryItems(mixed, options).map { it.identity.stableKey })
        assertEquals(expected, filterGalleryItems(mixed.reversed(), options).map { it.identity.stableKey })
    }
    @Test fun sizesSortNumericallyNotByFormattedLabels() {
        assertEquals(listOf(30L, 20L, 10L), filterGalleryItems(mixed, MediaFilter(sort = MediaSort.SizeDescending)).map { it.fileSize })
    }
    @Test fun invalidRangeDoesNotAccidentallyExposeAllItems() {
        assertTrue(filterGalleryItems(mixed, MediaFilter(minBytes = 21, maxBytes = 20)).isEmpty())
    }
}
