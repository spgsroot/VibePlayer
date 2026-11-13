package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSettingsEditTest {
    private val saved = ProviderSettings(
        baseUrl = "https://api.example.com/v1",
        model = "m1",
        apiKey = "secret",
        consented = true
    )

    @Test
    fun blankKeyKeepsTheStoredOne() {
        assertEquals("secret", saved.edited("https://api.example.com/v1", "m1", "  ").apiKey)
    }

    @Test
    fun typedKeyReplacesTheStoredOneAndIsTrimmed() {
        assertEquals("new-key", saved.edited("https://api.example.com/v1", "m1", " new-key ").apiKey)
    }

    @Test
    fun sameHostKeepsConsent() {
        assertTrue(saved.edited("https://API.example.com/other/", "m2", "").consented)
    }

    @Test
    fun otherHostAsksForConsentAgain() {
        val moved = saved.edited("https://elsewhere.example.org/v1", "m1", "")
        assertFalse(moved.consented)
        assertEquals("secret", moved.apiKey)
    }

    @Test
    fun urlAndModelAreTidied() {
        val edited = saved.edited("  https://api.example.com/v1/  ", " m3 ", "")
        assertEquals("https://api.example.com/v1", edited.baseUrl)
        assertEquals("m3", edited.model)
    }

    @Test
    fun hostIsReadableForTheConsentText() {
        assertEquals("api.example.com", ProviderUrl.host("https://Api.Example.com/v1"))
    }
}
