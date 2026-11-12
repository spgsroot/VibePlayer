package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl
import org.json.JSONObject

/**
 * Bunkr. Contract verified live on bunkr.cr:
 *  - `/a/<id>?advanced=1` lists the whole album on one page as a JS literal, `window.albumFiles`;
 *  - `POST dl.bunkr.cr/api/_001_v2 {"id":<numeric>}` gives the CDN host and path;
 *  - `GET glb-apisign.cdn.cr/sign?path=` gives a token valid for 2 hours;
 *  - the CDN answers `Range` with 206.
 * The hosts below change when Bunkr moves domains; they live here and nowhere else.
 */
class BunkrHost(private val http: Http) : FileHost {
    override val name = "Bunkr"

    override fun parse(url: HttpUrl): HostLink? {
        if (!BUNKR_HOST.matches(url.host)) return null
        val segments = url.pathSegments
        val id = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return when (segments[0]) {
            "a" -> HostLink.Album(id)
            "f", "v", "i", "d" -> HostLink.File(id)
            else -> null
        }
    }

    override fun album(id: String): HostAlbum {
        val html = http.get("$SITE/a/${enc(id)}?advanced=1", pageHeaders)
        val files = parseAlbumFiles(html)
        if (files.isEmpty()) throw HostException("Bunkr: album $id has no files or is not public")
        return HostAlbum(parseTitle(html) ?: id, files)
    }

    override fun single(id: String): HostFile {
        val html = http.get("$SITE/f/${enc(id)}", pageHeaders)
        val numericId = DOWNLOAD_LINK.find(html)?.groupValues?.get(1)
            ?: throw HostException("Bunkr: file $id was not found")
        return HostFile(numericId, "")
    }

    override fun resolve(file: HostFile): DirectLink {
        val resolved = JSONObject(
            http.postJson(
                "$API/api/_001_v2",
                JSONObject().put("id", file.id).toString(),
                pageHeaders
            )
        )
        val cdn = resolved.optString("mediafiles")
        val path = resolved.optString("path")
        if (cdn.isEmpty() || path.isEmpty()) throw HostException("Bunkr: file ${file.id} is unavailable")
        val original = resolved.optString("original").repairDoubleEncoding().ifBlank { file.name }

        val signed = JSONObject(http.get("$SIGNER/sign?path=${enc(path)}", pageHeaders))
        val token = signed.optString("token")
        val expires = signed.optLong("ex")
        if (token.isEmpty() || expires == 0L) throw HostException("Bunkr: could not sign the download")

        return DirectLink(
            url = "$cdn$path?n=${enc(original)}&token=$token&ex=$expires",
            name = original,
            headers = pageHeaders
        )
    }

    private val pageHeaders = mapOf("Referer" to "$SITE/", "Origin" to SITE)

    companion object {
        private const val SITE = "https://bunkr.cr"
        private const val API = "https://dl.bunkr.cr"
        private const val SIGNER = "https://glb-apisign.cdn.cr"
        private val BUNKR_HOST = Regex("^(www\\.)?bunkrr?\\.[a-z]+$")
        private val DOWNLOAD_LINK = Regex("""dl\.bunkr\.[a-z]+/file/(\d+)""")
        private val ENTRY_START = Regex("""\{\s*id:\s*(\d+)\s*,""")
        private val ORIGINAL = Regex("""original:\s*"((?:[^"\\]|\\.)*)"""")
        private val SIZE = Regex("""\bsize:\s*(\d+)""")
        private val H1 = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)

        /**
         * `window.albumFiles = [{ id: 1, name: "...", original: "...", size: 2, ... }, ...]` is a JS object literal
         * with unquoted keys, not JSON. Each entry is cut at the next `{ id:` so quotes or braces in a file name
         * cannot break the split.
         */
        internal fun parseAlbumFiles(html: String): List<HostFile> {
            val start = html.indexOf("albumFiles")
            if (start < 0) return emptyList()
            val body = html.substring(start)
            val starts = ENTRY_START.findAll(body).toList()
            return starts.mapIndexedNotNull { index, match ->
                val end = starts.getOrNull(index + 1)?.range?.first ?: body.length
                val chunk = body.substring(match.range.first, end)
                val original = ORIGINAL.find(chunk)?.groupValues?.get(1)?.unescapeJs()?.repairDoubleEncoding()
                    ?: return@mapIndexedNotNull null
                HostFile(
                    id = match.groupValues[1],
                    name = original,
                    size = SIZE.find(chunk)?.groupValues?.get(1)?.toLongOrNull()
                )
            }
        }

        internal fun parseTitle(html: String): String? =
            H1.find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.unescapeHtml()?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}
