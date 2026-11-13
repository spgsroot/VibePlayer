package ru.spgsroot.peachy.browser

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONTokener
import ru.spgsroot.peachy.browser.adblock.Hosts

enum class MediaKind { FILE, STREAM }

/** A video the page loads or embeds. [stream] items (HLS, DASH) cannot be saved as one file. */
data class FoundVideo(val url: String, val kind: MediaKind)

/** Finds downloadable videos on a page and decides whether a download target is acceptable. */
object MediaSniffer {
    private val FILE_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov")
    private val STREAM_EXTENSIONS = setOf("m3u8", "mpd")
    private const val MAX_FOUND = 40

    /** Classifies a request URL by the extension of its path, or null when it is not video. */
    fun classify(url: String): MediaKind? {
        val path = url.substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in FILE_EXTENSIONS -> MediaKind.FILE
            in STREAM_EXTENSIONS -> MediaKind.STREAM
            else -> null
        }
    }

    /**
     * Script run on demand (never injected into every page): reads `<video>` and `<source>`
     * elements and returns their addresses as a JSON string. Nothing is exposed to the page.
     */
    const val COLLECT_SCRIPT = "(function(){var o=[];function a(u){if(u&&typeof u==='string'&&u.indexOf('http')===0)o.push(u);}" +
        "document.querySelectorAll('video').forEach(function(v){a(v.currentSrc);a(v.src);});" +
        "document.querySelectorAll('video source,audio source').forEach(function(s){a(s.src);});" +
        "return JSON.stringify(o);})()"

    /** Parses what `evaluateJavascript(COLLECT_SCRIPT)` returned into videos; junk becomes empty. */
    fun parseCollected(raw: String?): List<FoundVideo> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return try {
            val value = JSONTokener(raw).nextValue()
            val array = JSONArray(value as? String ?: return emptyList())
            val out = LinkedHashMap<String, FoundVideo>()
            for (i in 0 until array.length()) {
                val url = array.optString(i)
                if (!BrowserUrls.isWebUrl(url)) continue
                // blob: sources are skipped above; a typeless URL is shown as a file, the user decides.
                val kind = classify(url) ?: MediaKind.FILE
                out.putIfAbsent(url, FoundVideo(url, kind))
                if (out.size >= MAX_FOUND) break
            }
            out.values.toList()
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /**
     * Downloads are started by a page's content, so a page must not be able to aim the app at the
     * user's own network. Loopback, private and link-local literals and `.local` names are
     * refused unless the user is browsing that very host. DNS names that resolve to such
     * addresses are not detected here.
     */
    fun isAllowedDownload(url: String, pageHost: String?): Boolean {
        if (!BrowserUrls.isWebUrl(url)) return false
        val host = Hosts.hostOf(url) ?: return false
        if (pageHost != null && host == pageHost) return true
        return !isPrivateHost(host)
    }

    internal fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return true
        if (host.contains(':')) {
            val h = host.lowercase()
            return h == "::1" || h == "::" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") ||
                h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb") || h.startsWith("::ffff:")
        }
        val labels = host.split('.')
        // Decimal (2130706433) and hex (0x7f.1) spellings of an address.
        if (labels.all { it.isNotEmpty() && it.all(Char::isDigit) } && labels.size < 4) return true
        if (labels.any { it.startsWith("0x") }) return true
        if (labels.size == 4 && labels.all { l -> l.isNotEmpty() && l.all(Char::isDigit) }) {
            val n = labels.map { it.toIntOrNull() ?: return true }
            return n[0] == 10 || n[0] == 127 || n[0] == 0 ||
                (n[0] == 169 && n[1] == 254) ||
                (n[0] == 172 && n[1] in 16..31) ||
                (n[0] == 192 && n[1] == 168) ||
                (n[0] == 100 && n[1] in 64..127)
        }
        return false
    }
}
