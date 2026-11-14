package ru.spgsroot.peachy.data.storage

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun declaredOversizeClosesInputAndCreatesNoFile() {
        val root = temporary.newFolder()
        val input = TrackingInput(byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) {
            PhotoFiles(root).save(input, "a.jpg", 101, 100)
        }
        assertTrue(input.closed)
        assertTrue(File(root, "photos").listFiles().orEmpty().isEmpty())
    }

    @Test fun defaultAndExplicitLimitsCannotExceedOneHundredMiB() {
        val files = PhotoFiles(temporary.newFolder())
        val limit = 100L * 1024 * 1024
        assertThrows(IllegalArgumentException::class.java) {
            files.save(ByteArrayInputStream(byteArrayOf(1)), "a.jpg", limit + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            files.save(ByteArrayInputStream(byteArrayOf(1)), "a.jpg", limit + 1, Long.MAX_VALUE)
        }
        val accepted = files.save(ByteArrayInputStream(byteArrayOf(1)), "a.jpg", limit)
        assertArrayEquals(byteArrayOf(1), accepted.readBytes())
    }

    @Test fun streamedOversizeRemovesPartialFileEvenWhenDeclaredSizeLies() {
        val root = temporary.newFolder()
        val input = TrackingInput(ByteArray(101))
        assertThrows(IllegalArgumentException::class.java) {
            PhotoFiles(root).save(input, "a.jpg", 1, 100)
        }
        assertTrue(input.closed)
        assertTrue(File(root, "photos").listFiles().orEmpty().isEmpty())
    }

    @Test fun exactLimitIsAcceptedAndUntrustedNamesCannotEscapeOrOverwrite() {
        val root = temporary.newFolder()
        val files = PhotoFiles(root)
        val first = files.save(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "../../private.JPG", 3, 3)
        val second = files.save(ByteArrayInputStream(byteArrayOf(4)), "../../private.JPG", 1, 3)
        assertEquals(File(root, "photos").canonicalFile, first.parentFile.canonicalFile)
        assertNotEquals(first, second)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertArrayEquals(byteArrayOf(4), second.readBytes())
        assertEquals("jpg", first.extension)
    }

    @Test fun ioFailureAndCancellationBothRemovePartialFilesAndCloseInput() {
        for (failure in listOf(IOException("broken provider"), CancellationException("cancelled"))) {
            val root = temporary.newFolder()
            var closed = false
            val input = object : InputStream() {
                private var firstRead = true
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    if (!firstRead) throw failure
                    firstRead = false
                    bytes[offset] = 1
                    return 1
                }
                override fun read(): Int = throw failure
                override fun close() { closed = true }
            }
            assertThrows(failure.javaClass) { PhotoFiles(root).save(input, "a.png") }
            assertTrue(closed)
            assertTrue(File(root, "photos").listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun thumbnailFailureRemovesPartialThumbnail() {
        val root = temporary.newFolder()
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("broken thumbnail")
        }
        assertThrows(IOException::class.java) { PhotoFiles(root).saveThumbnail(input, 42) }
        assertTrue(File(root, "photo_thumbnails").listFiles().orEmpty().isEmpty())
    }

    @Test fun thumbnailsWithUnsetIdsStillGetUniqueReservedNames() {
        val files = PhotoFiles(temporary.newFolder())
        val first = files.saveThumbnail(ByteArrayInputStream(byteArrayOf(1)), 0)
        val second = files.saveThumbnail(ByteArrayInputStream(byteArrayOf(2)), 0)
        assertNotEquals(first, second)
        assertArrayEquals(byteArrayOf(1), File(first).readBytes())
        assertArrayEquals(byteArrayOf(2), File(second).readBytes())
    }

    @Test fun deletionIsScopedAndMissingOwnedFilesAreIdempotent() {
        val root = temporary.newFolder()
        val files = PhotoFiles(root)
        val photo = files.save(ByteArrayInputStream(byteArrayOf(1)), "a.jpg")
        val thumb = files.saveThumbnail(ByteArrayInputStream(byteArrayOf(2)), 42)
        val outside = temporary.newFile().apply { writeText("private") }
        assertFalse(files.deleteFile(outside.absolutePath))
        assertFalse(files.deleteFile(thumb))
        assertFalse(files.deleteThumbnail(photo.absolutePath))
        assertFalse(files.deleteFile(root.absolutePath))
        assertTrue(files.deleteThumbnail(thumb))
        assertTrue(files.deleteFile(photo.absolutePath))
        assertTrue(files.deleteFile(photo.absolutePath))
        assertTrue(outside.exists())
    }

    private class TrackingInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
}
