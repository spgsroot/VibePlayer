package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NhentaiRequestTest {
    @Test
    fun searchRequestPassesSortAndKeepsTheKeyOutOfTheUrl() {
        val request = NhentaiRequest.search("test", "popular", 2, "secret-key")
        assertEquals("https://nhentai.net/api/v2/search?query=test&sort=popular&page=2", request.url)
        assertEquals("Key secret-key", request.header("Authorization"))
        assertFalse(request.url.contains("secret-key"))
        assertEquals("GET", request.method)
    }

    @Test
    fun searchRequestEncodesQueryAndSortAsSeparateParameters() {
        val request = NhentaiRequest.search("tag:english & 日本", "popular & recent", 3, "secret-key")
        assertEquals(
            "https://nhentai.net/api/v2/search?query=tag%3Aenglish+%26+%E6%97%A5%E6%9C%AC&sort=popular+%26+recent&page=3",
            request.url
        )
    }

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
