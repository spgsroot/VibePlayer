package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl
import org.json.JSONObject

/**
 * Filester. Verified live on filester.me: folders are server-rendered HTML at `/f/<id>?page=N` (24 files a page),
 * `POST /v2/api/public/download {"file_slug"}` returns a 30-minute token and the CDN server, and the CDN answers
 * Range with 206. Sub-folders and password folders are not handled.
 */
class FilesterHost(private val http: Http) : FileHost {
    override val name = "Filester"

    override fun parse(url: HttpUrl): HostLink? {
        if (!url.hostMatches("filester.me")) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return when (url.pathSegments[0]) {
            "f" -> HostLink.Album(id)
            "d" -> HostLink.File(id)
            else -> null
        }
    }

    override fun album(id: String): HostAlbum {
        val files = ArrayList<HostFile>()
        var title: String? = null
        for (page in 1..MAX_PAGES) {
            val html = http.get("$SITE/f/${enc(id)}?page=$page", pageHeaders)
            if (title == null) title = parseTitle(html)
            val items = parseFiles(html)
            if (items.isEmpty()) break
            files += items
            if (!html.contains("""href="?page=${page + 1}"""")) break
        }
        if (files.isEmpty()) throw HostException("Filester: folder $id is empty or does not exist")
        return HostAlbum(title ?: id, files.distinctBy { it.id })
    }

    override fun single(id: String) = HostFile(id, "")

    override fun resolve(file: HostFile): DirectLink {
        val grant = JSONObject(
            http.postJson(
                "$SITE/v2/api/public/download",
                JSONObject().put("file_slug", file.id).toString(),
                pageHeaders
            )
        )
        val server = grant.optString("server")
        val stored = grant.optString("file")
        val token = grant.optString("token")
        if (!grant.optBoolean("success") || server.isEmpty() || stored.isEmpty() || token.isEmpty()) {
            throw HostException("Filester: file ${file.id} is unavailable")
        }
        return DirectLink(
            url = "$server/v2/${enc(stored)}?token=${enc(token)}",
            name = grant.optString("name").ifBlank { file.name },
            headers = pageHeaders
        )
    }

    private val pageHeaders = mapOf("Referer" to "$SITE/")

    companion object {
        private const val SITE = "https://filester.me"
        private const val MAX_PAGES = 200
        private val ITEM = Regex(
            """data-name="([^"]*)"[^>]*?data-size="(\d+)"[^>]*?(?:window\.location\.href='/d/([^']+)'|downloadFile\('([^']+)'\))"""
        )
        private val H1 = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)

        internal fun parseFiles(html: String): List<HostFile> =
            ITEM.findAll(html).mapNotNull {
                val slug = it.groupValues[3].ifEmpty { it.groupValues[4] }.ifEmpty { return@mapNotNull null }
                HostFile(slug, it.groupValues[1].unescapeHtml(), it.groupValues[2].toLongOrNull())
            }.toList()

        internal fun parseTitle(html: String): String? =
            H1.find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.unescapeHtml()?.trim()
                ?.takeIf { it.isNotEmpty() }
    }
}
