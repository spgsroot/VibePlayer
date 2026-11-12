package ru.spgsroot.peachy.data.manga

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.spgsroot.peachy.manga.GalleryComment
import ru.spgsroot.peachy.manga.GalleryDetail
import ru.spgsroot.peachy.manga.GallerySummary
import ru.spgsroot.peachy.manga.GalleryTag
import ru.spgsroot.peachy.manga.NhentaiParser
import ru.spgsroot.peachy.manga.NhentaiRequest
import ru.spgsroot.peachy.manga.NhentaiSecrets
import ru.spgsroot.peachy.manga.TagPage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class NhentaiException(message: String) : Exception(message)

@Singleton
class NhentaiApi @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tagCache = ConcurrentHashMap<Int, GalleryTag>()

    fun search(query: String, sort: String, page: Int = 1): List<GallerySummary> {
        val request = NhentaiRequest.search(query, sort, page, NhentaiSecrets.API_KEY)
        return withTags(NhentaiParser.search(execute(request)))
    }

    fun gallery(id: Int): GalleryDetail {
        val request = NhentaiRequest.gallery(id, NhentaiSecrets.API_KEY)
        return NhentaiParser.gallery(execute(request))
    }

    fun tags(type: String, query: String = "", page: Int = 1): TagPage {
        val key = NhentaiSecrets.API_KEY
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val body = execute(NhentaiRequest.tagList(type, page, TAG_PAGE_SIZE, key))
            return NhentaiParser.tagsPage(body, page)
        }
        // Autocomplete has no paging: only the first page exists.
        if (page > 1) return TagPage(emptyList(), hasNext = false)
        val body = execute(NhentaiRequest.tagSearch(type, trimmed, TAG_SEARCH_LIMIT, key))
        return TagPage(NhentaiParser.tags(body), hasNext = false)
    }

    fun randomId(): Int =
        NhentaiParser.randomGalleryId(execute(NhentaiRequest.random(NhentaiSecrets.API_KEY)))

    fun related(id: Int): List<GallerySummary> =
        withTags(NhentaiParser.related(execute(NhentaiRequest.related(id, NhentaiSecrets.API_KEY))))

    fun comments(id: Int): List<GalleryComment> =
        NhentaiParser.comments(execute(NhentaiRequest.comments(id, NhentaiSecrets.API_KEY)))

    fun setFavorite(id: Int, favorite: Boolean) {
        execute(NhentaiRequest.favorite(id, NhentaiSecrets.API_KEY, favorite))
    }

    fun download(url: String): ByteArray {
        val call = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", NhentaiRequest.USER_AGENT)
                .build()
        )
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw NhentaiException("Download failed: ${response.code}")
            }
            return response.body?.bytes() ?: throw NhentaiException("Empty download")
        }
    }

    /**
     * Resolves card tag ids with one `/tags/ids` call per 100 unseen ids instead of one call per card.
     * A failed lookup only leaves those cards without tag chips; the results themselves still load.
     */
    private fun withTags(cards: List<GallerySummary>): List<GallerySummary> {
        val missing = cards.flatMap { it.tagIds }.filterNot(tagCache::containsKey).distinct()
        missing.chunked(TAGS_PER_LOOKUP).forEach { chunk ->
            try {
                val body = execute(NhentaiRequest.tagsByIds(chunk, NhentaiSecrets.API_KEY))
                NhentaiParser.tags(body).forEach { tagCache[it.id] = it }
            } catch (_: java.io.IOException) {
            } catch (_: NhentaiException) {
            } catch (_: org.json.JSONException) {
            }
        }
        return cards.map { card -> card.copy(tags = card.tagIds.mapNotNull(tagCache::get)) }
    }

    private fun execute(request: NhentaiRequest): String {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val body = when {
            request.body != null -> request.body.toByteArray(Charsets.UTF_8).toRequestBody(JSON_MEDIA_TYPE)
            request.method == "GET" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        builder.method(request.method, body)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 429) throw NhentaiException("Rate limited")
            if (!response.isSuccessful) throw NhentaiException("nhentai ${response.code}")
            return text
        }
    }

    private companion object {
        const val TAGS_PER_LOOKUP = 100
        const val TAG_PAGE_SIZE = 50
        const val TAG_SEARCH_LIMIT = 50
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
