package ru.spgsroot.peachy.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.spgsroot.peachy.browser.adblock.ResourceType

class RequestKindsTest {
    @Test
    fun extensionWinsAndIgnoresQueryAndFragment() {
        assertEquals(ResourceType.SCRIPT, RequestKinds.guess("https://a.com/x/app.JS?v=1#f", null, false))
        assertEquals(ResourceType.IMAGE, RequestKinds.guess("https://a.com/p.webp", mapOf("Accept" to "text/css"), false))
        assertEquals(ResourceType.MEDIA, RequestKinds.guess("https://a.com/v/seg1.m4s", null, false))
    }

    @Test
    fun headersDecideWhenThereIsNoExtension() {
        assertEquals(ResourceType.STYLESHEET, RequestKinds.guess("https://a.com/s", mapOf("accept" to "text/css,*/*;q=0.1"), false))
        assertEquals(ResourceType.XHR, RequestKinds.guess("https://a.com/api", mapOf("X-Requested-With" to "XMLHttpRequest"), false))
        assertEquals(ResourceType.SUBDOCUMENT, RequestKinds.guess("https://a.com/frame", mapOf("Accept" to "text/html,application/xhtml+xml"), false))
        assertEquals(ResourceType.OTHER, RequestKinds.guess("https://a.com/x", emptyMap(), false))
    }

    @Test
    fun mainFrameIsNeverASubresource() {
        assertEquals(ResourceType.SUBDOCUMENT, RequestKinds.guess("https://a.com/ad.js", null, true))
    }
}
