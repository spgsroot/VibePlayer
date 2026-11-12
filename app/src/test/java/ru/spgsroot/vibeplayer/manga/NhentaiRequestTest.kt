package ru.spgsroot.vibeplayer.manga

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NhentaiRequestTest {
    @Test
    fun blankSearchBrowsesWithWildcardBecauseTheApiRejectsEmptyQueries() {
        val request = NhentaiRequest.search("  ", "date", 3, "k")

        assertEquals("https://nhentai.net/api/v2/search?query=*&sort=date&page=3", request.url)
    }

    @Test
    fun searchEncodesQueryAndSort() {
        val request = NhentaiRequest.search("language:english \"school uniform\"", "popular-today", 1, "k")

        assertEquals(
            "https://nhentai.net/api/v2/search?query=language%3Aenglish+%22school+uniform%22&sort=popular-today&page=1",
            request.url
        )
    }

    @Test
    fun tagSearchPostsJsonBodyWithContentType() {
        val request = NhentaiRequest.tagSearch("artist", "mu", 50, "k")

        assertEquals("POST", request.method)
        assertEquals("application/json", request.header("Content-Type"))
        val body = JSONObject(request.body!!)
        assertEquals("mu", body.getString("query"))
        assertEquals("artist", body.getString("type"))
        assertEquals(50, body.getInt("limit"))
    }

    @Test
    fun getRequestsCarryNoBodyAndTagIdsAreCommaSeparated() {
        val request = NhentaiRequest.tagsByIds(listOf(9, 4, 7), "k")

        assertEquals("GET", request.method)
        assertNull(request.body)
        assertEquals("https://nhentai.net/api/v2/tags/ids?ids=9,4,7", request.url)
    }
}
