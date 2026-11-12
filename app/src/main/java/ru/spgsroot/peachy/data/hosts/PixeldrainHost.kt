package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl
import org.json.JSONObject

/** Pixeldrain's public JSON API, verified live: `/api/list/<id>`, `/api/file/<id>/info`, `/api/file/<id>` (Range works). */
class PixeldrainHost(private val http: Http) : FileHost {
    override val name = "Pixeldrain"

    override fun parse(url: HttpUrl): HostLink? {
        if (!url.hostMatches("pixeldrain.com", "pixeldrain.net", "pixeldra.in")) return null
        val segments = url.pathSegments
        return when {
            segments.getOrNull(0) == "l" && !segments.getOrNull(1).isNullOrBlank() -> HostLink.Album(segments[1])
            segments.getOrNull(0) == "u" && !segments.getOrNull(1).isNullOrBlank() -> HostLink.File(segments[1])
            segments.getOrNull(0) == "api" && segments.getOrNull(1) == "file" && !segments.getOrNull(2).isNullOrBlank() ->
                HostLink.File(segments[2])
            else -> null
        }
    }

    override fun album(id: String): HostAlbum {
        val list = JSONObject(http.get("$API/list/${enc(id)}"))
        val files = list.optJSONArray("files")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val fileId = item.optString("id").ifEmpty { return@mapNotNull null }
                HostFile(fileId, item.optString("name"), item.optLong("size", -1).takeIf { it >= 0 })
            }
        }.orEmpty()
        if (files.isEmpty()) throw HostException("Pixeldrain: list $id is empty or does not exist")
        return HostAlbum(list.optString("title").ifBlank { id }, files)
    }

    override fun single(id: String): HostFile {
        val info = JSONObject(http.get("$API/file/${enc(id)}/info"))
        return HostFile(id, info.optString("name"), info.optLong("size", -1).takeIf { it >= 0 })
    }

    override fun resolve(file: HostFile) =
        DirectLink("$API/file/${enc(file.id)}", file.name, emptyMap())

    private companion object {
        const val API = "https://pixeldrain.com/api"
    }
}
