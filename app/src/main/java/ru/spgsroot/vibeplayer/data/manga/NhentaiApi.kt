package ru.spgsroot.vibeplayer.data.manga

import okhttp3.OkHttpClient
import okhttp3.Request
import ru.spgsroot.vibeplayer.manga.GalleryDetail
import ru.spgsroot.vibeplayer.manga.GallerySummary
import ru.spgsroot.vibeplayer.manga.NhentaiParser
import ru.spgsroot.vibeplayer.manga.NhentaiRequest
import ru.spgsroot.vibeplayer.manga.NhentaiSecrets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class NhentaiException(message: String) : Exception(message)

@Singleton
class NhentaiApi @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    fun search(query: String, sort: String, page: Int = 1): List<GallerySummary> {
        val request = NhentaiRequest.search(query, sort, page, NhentaiSecrets.API_KEY)
        return NhentaiParser.search(execute(request))
    }

    fun gallery(id: Int): GalleryDetail {
        val request = NhentaiRequest.gallery(id, NhentaiSecrets.API_KEY)
        return NhentaiParser.gallery(execute(request))
    }

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

    private fun execute(request: NhentaiRequest): String {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        builder.method(request.method, if (request.method == "GET") null else okhttp3.RequestBody.create(null, ByteArray(0)))
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 429) throw NhentaiException("Rate limited")
            if (!response.isSuccessful) throw NhentaiException("nhentai ${response.code}")
            return body
        }
    }
}
