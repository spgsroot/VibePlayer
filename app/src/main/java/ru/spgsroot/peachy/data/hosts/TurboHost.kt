package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl
import org.json.JSONObject

/**
 * Turbo. Verified live on turbo.cr: the album page embeds `const FILES = [...]`, and `GET /api/sign?v=<id>`
 * returns a 2-hour signed URL without a captcha. The CDN (turbocdn.st) was unreachable from the machine used
 * for probing, so the Range behaviour of the signed URL itself is not verified.
 */
class TurboHost(private val http: Http) : FileHost {
    override val name = "Turbo"

    override fun parse(url: HttpUrl): HostLink? {
        if (!TURBO_HOST.matches(url.host)) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return when (url.pathSegments[0]) {
            "a" -> HostLink.Album(id)
            "v", "embed", "d" -> HostLink.File(id)
            else -> null
        }
    }

    override fun album(id: String): HostAlbum {
        val html = http.get("$SITE/a/${enc(id)}", pageHeaders)
        val files = parseAlbumFiles(html)
        if (files.isEmpty()) throw HostException("Turbo: album $id has no files or is not public")
        return HostAlbum(parseTitle(html) ?: id, files)
    }

    override fun single(id: String) = HostFile(id, "")

    override fun resolve(file: HostFile): DirectLink {
        val signed = JSONObject(http.get("$SITE/api/sign?v=${enc(file.id)}", pageHeaders))
        val url = signed.optString("url")
        if (!signed.optBoolean("success") || url.isEmpty()) throw HostException("Turbo: file ${file.id} is unavailable")
        val name = signed.optString("original_filename").ifBlank { signed.optString("filename") }
        return DirectLink(url, name.ifBlank { file.name }, pageHeaders)
    }

    private val pageHeaders = mapOf("Referer" to "$SITE/", "Origin" to SITE)

    companion object {
        private const val SITE = "https://turbo.cr"
        private val TURBO_HOST = Regex("^(www\\.)?turbo\\.[a-z]+$")
        private val ENTRY = Regex("""\{\s*id:\s*"([^"]+)"\s*,\s*name:\s*"((?:[^"\\]|\\.)*)"\s*,\s*size:\s*(\d+)""")
        private val H1 = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)

        /** `FILES` is a JS literal (unquoted keys, `\/`-escaped URLs), not JSON, so entries are matched one by one. */
        internal fun parseAlbumFiles(html: String): List<HostFile> {
            val start = html.indexOf("const FILES")
            if (start < 0) return emptyList()
            return ENTRY.findAll(html, start).map {
                HostFile(it.groupValues[1], it.groupValues[2].unescapeJs(), it.groupValues[3].toLongOrNull())
            }.toList()
        }

        internal fun parseTitle(html: String): String? =
            H1.find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.unescapeHtml()?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}
