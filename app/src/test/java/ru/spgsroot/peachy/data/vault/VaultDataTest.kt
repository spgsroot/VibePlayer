package ru.spgsroot.peachy.data.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultDataTest {
    private fun empty(translations: List<VaultTranslation> = emptyList()) = VaultData(
        entryCount = 0, videos = emptyList(), galleries = emptyList(), readHistory = emptyList(),
        searches = emptyList(), blacklist = emptyList(), memory = emptyList(), notes = emptyList(),
        bookmarks = emptyList(), browserHistory = emptyList(), allowlist = emptyList(), tagLibrary = null,
        translations = translations
    )

    @Test
    fun olderVaultWithoutTranslationsReadsAsEmpty() {
        val older = JSONObject(empty().toJson()).apply { remove("translations") }.toString()
        assertTrue(VaultData.fromJson(older).translations.isEmpty())
    }

    @Test
    fun translationsRoundTrip() {
        val data = empty(
            listOf(
                VaultTranslation(7, 0, "ru", "glm-5.3-flash", "{\"sourceLanguage\":\"ja\",\"lines\":[]}", 100),
                VaultTranslation(7, 12, "en", "m \"x\"", "{\"a\":\"line\\nbreak\"}", 5)
            )
        )
        assertEquals(data, VaultData.fromJson(data.toJson()))
    }

    @Test
    fun aTranslationMissingAFieldMakesTheVaultCorrupt() {
        val broken = JSONObject(empty().toJson())
            .put("translations", org.json.JSONArray().put(JSONObject().put("galleryId", 1).put("page", 0)))
            .toString()
        val error = org.junit.Assert.assertThrows(VaultException::class.java) { VaultData.fromJson(broken) }.error
        assertEquals(VaultError.CORRUPT, error)
    }
}
