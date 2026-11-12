package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl
import org.json.JSONObject

/**
 * Cyberdrop. Contract verified live on cyberdrop.cr: the album page lists files in plain HTML, then
 * `api.cyberdrop.cr/api/file/info/<slug>` gives the name and `/api/file/auth/<slug>` a 24-hour signed CDN URL.
 * The CDN host comes back in that URL and changes, so it is never hard-coded.
 */
class CyberdropHost(private val http: Http) : FileHost {
    override val name = "Cyberdrop"

    override fun parse(url: HttpUrl): HostLink? {
        if (!CYBERDROP_HOST.matches(url.host)) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return when (url.pathSegments[0]) {
            "a" -> HostLink.Album(id)
            "f", "e" -> HostLink.File(id)
            else -> null
        }
    }

    override fun album(id: String): HostAlbum {
        val html = http.get("$SITE/a/${enc(id)}", pageHeaders)
        val files = parseAlbumFiles(html)
        if (files.isEmpty()) throw HostException("Cyberdrop: album $id has no files or is not public")
        return HostAlbum(parseTitle(html) ?: id, files)
    }

    override fun single(id: String) = HostFile(id, "")

    override fun resolve(file: HostFile): DirectLink {
        val info = JSONObject(http.get("$API/file/info/${enc(file.id)}", pageHeaders))
        val auth = JSONObject(http.get("$API/file/auth/${enc(file.id)}", pageHeaders))
        val url = auth.optString("url")
        if (url.isEmpty()) throw HostException("Cyberdrop: file ${file.id} is unavailable")
        return DirectLink(url, info.optString("name").ifBlank { file.name }, pageHeaders)
    }

    private val pageHeaders = mapOf("Referer" to "$SITE/")

    companion object {
        private const val SITE = "https://cyberdrop.cr"
        private const val API = "https://api.cyberdrop.cr/api"
        private val CYBERDROP_HOST = Regex("^(www\\.)?cyberdrop\\.[a-z]+$")
        private val FILE_TAG = Regex("""<a\s[^>]*\bid="file"[^>]*>""")
        private val HREF = Regex("""href="/f/([A-Za-z0-9_-]+)"""")
        private val TITLE_ATTR = Regex("""\btitle="([^"]*)"""")
        private val H1_TITLE = Regex("""<h1[^>]*\bid="title"[^>]*>""")

        internal fun parseAlbumFiles(html: String): List<HostFile> =
            FILE_TAG.findAll(html).mapNotNull { tag ->
                val text = tag.value
                val slug = HREF.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
                val title = TITLE_ATTR.find(text)?.groupValues?.get(1)?.unescapeHtml().orEmpty()
                HostFile(slug, title)
            }.distinctBy { it.id }.toList()

        internal fun parseTitle(html: String): String? {
            val tag = H1_TITLE.find(html)?.value ?: return null
            return TITLE_ATTR.find(tag)?.groupValues?.get(1)?.unescapeHtml()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
