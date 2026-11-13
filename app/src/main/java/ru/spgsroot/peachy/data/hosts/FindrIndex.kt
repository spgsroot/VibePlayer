package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * findr.to indexes albums that live on other hosts. Its album page shows only part of a large album, but
 * always names the album on the original host (the "Source" button), so that link is what gets imported.
 */
class FindrIndex(private val http: Http) {
    /** True for `findr.to/albums/<number>`; the host must be findr.to itself, not a lookalike. */
    fun recognizes(url: HttpUrl): Boolean {
        if (!url.hostMatches("findr.to")) return false
        val segments = url.pathSegments
        return segments.size == 2 && segments[0] == "albums" && segments[1].isNotEmpty() && segments[1].all { it in '0'..'9' }
    }

    /** The album link on the original host for the findr album at [url]. */
    fun sourceOf(url: HttpUrl): String {
        val id = url.pathSegments[1]
        // Always ask findr.to itself, whatever subdomain the link came with.
        val html = http.get("https://findr.to/albums/$id")
        val source = parseSource(html) ?: throw HostException("findr: album $id has no source link")
        return source
    }

    internal companion object {
        // `<a class="..." href="https://turbo.cr/a/abc">Source<svg ...></svg></a>`
        private val SOURCE_ANCHOR = Regex("""<a\b[^>]*?\bhref="([^"]+)"[^>]*>\s*Source\s*<""")

        internal fun parseSource(html: String): String? =
            SOURCE_ANCHOR.find(html)?.groupValues?.get(1)?.unescapeHtml()?.trim()
                ?.takeIf { it.toHttpUrlOrNull() != null }
    }
}
