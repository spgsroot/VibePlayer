package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NhentaiRequestTest {
    @Test
    fun favoriteRequestCarriesTheEmbeddedKeyAndNotTheUrl() {
        val request = NhentaiRequest.favorite(galleryId = 9, key = "secret-key", add = true)
        assertEquals("https://nhentai.net/api/v2/galleries/9/favorite", request.url)
        assertEquals("Key secret-key", request.header("Authorization"))
        assertFalse(request.url.contains("secret-key"))
        assertEquals("POST", request.method)
    }

    @Test
    fun blankKeyIsNotEmbedded() {
        assertTrue(NhentaiSecrets.API_KEY.isNotBlank())
    }
}
