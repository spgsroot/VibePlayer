package ru.spgsroot.peachy.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class VideoFileDeletionTest {
    @Test
    fun resolvesFileStoredUnderVideosDir() {
        val root = File("build/video-deletion-test").apply { mkdirs() }
        val video = File(root, "1710000000000.mp4").apply { writeText("video") }

        val resolved = VideoFileDeletion.resolve(root, video.absolutePath)

        assertEquals(video.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun rejectsPathOutsideVideosDir() {
        val root = File("build/video-deletion-test-root").apply { mkdirs() }
        val outside = File("build/video-deletion-test-outside.txt").apply { writeText("secret") }

        assertNull(VideoFileDeletion.resolve(root, outside.absolutePath))
    }
}
