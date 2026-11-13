package ru.spgsroot.peachy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSnifferTest {
    @Test
    fun classifiesByPathExtensionIgnoringQueryAndFragment() {
        assertEquals(MediaKind.FILE, MediaSniffer.classify("https://c.com/v/clip.MP4?token=a.b"))
        assertEquals(MediaKind.STREAM, MediaSniffer.classify("https://c.com/hls/master.m3u8#x"))
        assertNull(MediaSniffer.classify("https://c.com/page.html?file=a.mp4"))
        assertNull(MediaSniffer.classify("https://c.com/"))
    }

    @Test
    fun collectedResultIsParsedAndFilteredToWebUrls() {
        val raw = "\"[\\\"https://c.com/a.mp4\\\",\\\"blob:https://c.com/x\\\",\\\"https://c.com/a.mp4\\\",\\\"https://c.com/live.m3u8\\\",\\\"javascript:1\\\"]\""
        val found = MediaSniffer.parseCollected(raw)
        assertEquals(
            listOf(FoundVideo("https://c.com/a.mp4", MediaKind.FILE), FoundVideo("https://c.com/live.m3u8", MediaKind.STREAM)),
            found
        )
    }

    @Test
    fun junkFromThePageYieldsNothing() {
        assertTrue(MediaSniffer.parseCollected(null).isEmpty())
        assertTrue(MediaSniffer.parseCollected("null").isEmpty())
        assertTrue(MediaSniffer.parseCollected("\"not json\"").isEmpty())
        assertTrue(MediaSniffer.parseCollected("42").isEmpty())
    }

    @Test
    fun downloadsCannotTargetThePrivateNetworkUnlessThePageIsThere() {
        for (h in listOf("127.0.0.1", "localhost", "10.1.2.3", "192.168.0.5", "172.20.1.1", "169.254.1.1", "[::1]", "2130706433", "0x7f.0.0.1", "nas.local", "100.64.0.1")) {
            assertFalse(h, MediaSniffer.isAllowedDownload("http://$h/v.mp4", pageHost = "evil.com"))
        }
        assertTrue(MediaSniffer.isAllowedDownload("https://cdn.example.com/v.mp4", "evil.com"))
        assertTrue(MediaSniffer.isAllowedDownload("http://192.168.0.5/v.mp4", "192.168.0.5"))
        assertTrue(MediaSniffer.isAllowedDownload("https://172.32.0.1/v.mp4", null))
        assertFalse(MediaSniffer.isAllowedDownload("file:///sdcard/v.mp4", null))
        assertFalse(MediaSniffer.isAllowedDownload("ftp://h/v.mp4", null))
    }
}
