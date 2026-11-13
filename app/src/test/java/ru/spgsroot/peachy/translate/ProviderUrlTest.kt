package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUrlTest {
    @Test
    fun httpsAndPrivateHttpAreAllowed() {
        listOf(
            "https://opencode.ai/inference/go/v1",
            "HTTPS://Example.com/v1",
            "http://10.0.2.2:8765/v1",
            "http://localhost:1234/v1",
            "http://127.0.0.1:1234/v1",
            "http://192.168.1.5/v1",
            "http://172.16.0.1/v1",
            "http://172.20.0.1/v1",
            "http://172.31.255.255/v1"
        ).forEach { assertTrue(it, ProviderUrl.isAllowed(it)) }
    }

    @Test
    fun everythingElseIsRefused() {
        listOf(
            "http://example.com/v1",
            "ftp://x",
            "https://user@evil.com/v1",
            "https://user:pw@evil.com/v1",
            "http://10.evil.com/v1",
            "http://172.32.0.1/v1",
            "http://172.15.0.1/v1",
            "http://192.169.1.1/v1",
            "http://10.0.0.256/v1",
            "http://010.0.0.1/v1",
            "http://10.00.0.1/v1",
            "https://example.com:99999/v1",
            "https://",
            "",
            "   ",
            "not a url"
        ).forEach { assertFalse(it, ProviderUrl.isAllowed(it)) }
    }

    @Test
    fun keyMustBeAPlainHeaderValue() {
        assertTrue(ProviderSettings(apiKey = "oc_sk_AbC-123").isConfigured)
        assertFalse(ProviderSettings(apiKey = "ключ").isConfigured)
        assertFalse(ProviderSettings(apiKey = "a\u00a0b").isConfigured)
        assertFalse(ProviderSettings(apiKey = "has space").isConfigured)
    }

    @Test
    fun normalizedTrimsSpacesAndTrailingSlashes() {
        assertEquals("https://a.b/v1", ProviderUrl.normalized("  https://a.b/v1/ "))
        assertEquals("https://a.b", ProviderUrl.normalized("https://a.b///"))
    }

    @Test
    fun sessionHeaderOnlyForOpencodeHosts() {
        assertTrue(ProviderUrl.sendsSessionHeader("https://opencode.ai/x"))
        assertTrue(ProviderUrl.sendsSessionHeader("https://api.opencode.ai/x"))
        assertFalse(ProviderUrl.sendsSessionHeader("https://notopencode.ai/x"))
        assertFalse(ProviderUrl.sendsSessionHeader("https://opencode.ai.evil.com/x"))
        assertFalse(ProviderUrl.sendsSessionHeader("http://10.0.2.2:8766/v1"))
        assertFalse(ProviderUrl.sendsSessionHeader("not a url"))
    }

    @Test
    fun configuredNeedsKeyModelAndAllowedUrl() {
        val ok = ProviderSettings(apiKey = "k")
        assertTrue(ok.isConfigured)
        assertFalse(ok.copy(apiKey = " ").isConfigured)
        assertFalse(ok.copy(model = "").isConfigured)
        assertFalse(ok.copy(baseUrl = "http://example.com/v1").isConfigured)
    }

    @Test
    fun keyIsMaskedInToString() {
        val text = ProviderSettings(apiKey = "super-secret-value").toString()
        assertFalse(text, text.contains("super-secret-value"))
    }
}
