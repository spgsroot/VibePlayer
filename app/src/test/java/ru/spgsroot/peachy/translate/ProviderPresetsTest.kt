package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPresetsTest {
    @Test
    fun everyPresetIsUsableAsItStands() {
        ProviderPresets.all.forEach {
            assertTrue(it.name, ProviderUrl.isAllowed(it.baseUrl))
            assertTrue(it.name, it.baseUrl.startsWith("https://"))
            assertTrue(it.name, it.model.isNotBlank() && it.model == it.model.trim())
        }
    }

    @Test
    fun presetsDoNotRepeatAnAddress() {
        val urls = ProviderPresets.all.map { ProviderUrl.normalized(it.baseUrl) }
        assertEquals(urls.size, urls.toSet().size)
    }

    @Test
    fun defaultProviderIsOneOfThePresets() {
        assertNotNull(ProviderPresets.find(DEFAULT_BASE_URL))
    }

    @Test
    fun presetIsFoundWhateverTheSlashOrCase() {
        val first = ProviderPresets.all.first()
        assertEquals(first, ProviderPresets.find(first.baseUrl.uppercase() + "/"))
        assertEquals(first, ProviderPresets.find("  ${first.baseUrl}/  "))
    }

    @Test
    fun anAddressNoPresetOwnsIsNotFound() {
        assertNull(ProviderPresets.find("https://example.com/v1"))
        assertNull(ProviderPresets.find(""))
    }

    @Test
    fun onlyOpenAiAsksForCompletionTokens() {
        assertTrue(ProviderUrl.usesCompletionTokens("https://api.openai.com/v1"))
        assertTrue(ProviderUrl.usesCompletionTokens(" HTTPS://API.OPENAI.COM/v1/ "))
        assertFalse(ProviderUrl.usesCompletionTokens("https://api.openai.com.evil.com/v1"))
        assertFalse(ProviderUrl.usesCompletionTokens("https://opencode.ai/inference/go/v1"))
        assertFalse(ProviderUrl.usesCompletionTokens("not a url"))
    }
}
