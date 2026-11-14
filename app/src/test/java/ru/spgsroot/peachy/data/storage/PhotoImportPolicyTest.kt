package ru.spgsroot.peachy.data.storage

import org.junit.Assert.*
import org.junit.Test

class PhotoImportPolicyTest {
    @Test fun unreadableOrEmptyBoundsAreRejected() {
        for ((width, height) in listOf(-1 to -1, 0 to 10, 10 to 0)) {
            assertThrows(IllegalArgumentException::class.java) {
                PhotoImportPolicy.requireImageBounds(width, height)
            }
        }
        PhotoImportPolicy.requireImageBounds(640, 480)
    }

    @Test fun thumbnailDecodeSamplesLargeImagesWithoutUpscalingSmallImages() {
        assertEquals(1, PhotoImportPolicy.sampleSize(100, 80))
        assertEquals(8, PhotoImportPolicy.sampleSize(4000, 3000))
        assertEquals(1 shl 22, PhotoImportPolicy.sampleSize(Int.MAX_VALUE, 1))
    }

    @Test fun displayNamesAreSanitizedAndHaveSafeFallbacks() {
        assertEquals("holiday", PhotoImportPolicy.title("../../holiday.jpg"))
        assertEquals("holiday", PhotoImportPolicy.title("C:\\private\\holiday.PNG"))
        assertEquals("Photo", PhotoImportPolicy.title(null))
        assertEquals("Photo", PhotoImportPolicy.title(" \u0000\n "))
        assertEquals("Photo", PhotoImportPolicy.title("../.."))
        assertEquals("Photo", PhotoImportPolicy.title(".jpg"))
        assertEquals("a b", PhotoImportPolicy.title("a\nb.jpg"))
        assertTrue(PhotoImportPolicy.title("x".repeat(500)).length <= 200)
    }
}
