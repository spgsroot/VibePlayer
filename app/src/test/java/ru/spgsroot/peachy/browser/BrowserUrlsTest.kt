package ru.spgsroot.peachy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlsTest {
    @Test
    fun typedAddressesBecomeHttpsAndPlainHttpIsUpgraded() {
        assertEquals("https://example.com", BrowserUrls.resolve("example.com"))
        assertEquals("https://example.com/a?b=1", BrowserUrls.resolve("  https://example.com/a?b=1 "))
        assertEquals("https://example.com/x", BrowserUrls.resolve("http://example.com/x"))
        assertEquals("https://localhost:8080/p", BrowserUrls.resolve("localhost:8080/p"))
    }

    @Test
    fun wordsBecomeAnEncodedSearch() {
        assertEquals(BrowserUrls.SEARCH_URL + "cats+%26+dogs", BrowserUrls.resolve("cats & dogs"))
        assertEquals(BrowserUrls.SEARCH_URL + "hello", BrowserUrls.resolve("hello"))
        assertNull(BrowserUrls.resolve("   "))
    }

    @Test
    fun dangerousSchemesAreNeverLoadedOrSearched() {
        for (s in listOf("javascript:alert(1)", "file:///sdcard/a", "intent://x#Intent;end", "content://x/y", "data:text/html,<b>", "ftp://h/f", "JavaScript:1", "about:blank")) {
            assertNull(s, BrowserUrls.resolve(s))
        }
    }

    @Test
    fun trackingParametersAreRemovedAndTheRestIsKeptInOrder() {
        assertEquals(
            "https://a.com/p?id=7&sort=asc#top",
            BrowserUrls.stripTracking("https://a.com/p?utm_source=x&id=7&fbclid=abc&sort=asc&UTM_medium=y#top")
        )
        assertEquals("https://a.com/p#frag", BrowserUrls.stripTracking("https://a.com/p?gclid=1&utm_campaign=z#frag"))
        assertEquals("https://a.com/p?q=a%20b", BrowserUrls.stripTracking("https://a.com/p?q=a%20b"))
        assertEquals("https://a.com/", BrowserUrls.stripTracking("https://a.com/"))
    }

    @Test
    fun onlyWebUrlsAreWebUrls() {
        assertTrue(BrowserUrls.isWebUrl("HTTPS://a.com"))
        assertFalse(BrowserUrls.isWebUrl("intent://a"))
        assertFalse(BrowserUrls.isWebUrl("file:///a"))
    }
}
