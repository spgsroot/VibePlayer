package ru.spgsroot.peachy.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VideoExporterTest {
    @Test
    fun pathSeparatorsAndTraversalCannotEscapeTheFolder() {
        val name = exportFileName("../../etc/passwd", "mp4")
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
        assertFalse(name.startsWith("."))
        assertEquals("etc passwd.mp4", name)
    }

    @Test
    fun forbiddenCharactersAndControlCodesBecomeSingleSpaces() {
        assertEquals("a b c.mp4", exportFileName("a:\"b\u0000*  c?", "mp4"))
    }

    @Test
    fun blankTitleFallsBackAndExtensionIsSanitised() {
        assertEquals("video.mp4", exportFileName("  ...  ", ""))
        assertEquals("clip.mp4", exportFileName("clip", "m/p4"))
    }

    @Test
    fun longTitlesAreBoundedKeepingTheExtension() {
        val name = exportFileName("x".repeat(500), "webm")
        assertEquals(120 + ".webm".length, name.length)
        assertEquals("webm", name.substringAfterLast('.'))
    }

    @Test
    fun unicodeTitlesSurvive() {
        assertEquals("Видео 動画.mkv", exportFileName("Видео 動画", "MKV"))
    }
}
