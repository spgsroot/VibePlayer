package ru.spgsroot.peachy.browser

import java.net.URLEncoder
import ru.spgsroot.peachy.browser.adblock.Hosts

object BrowserUrls {
    const val SEARCH_URL = "https://duckduckgo.com/?q="

    private val EXPLICIT_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")
    private val OPAQUE_SCHEME = Regex("^(javascript|data|file|intent|content|about|view-source|blob|vbscript|mailto|tel|sms|market):", RegexOption.IGNORE_CASE)
    private val HOSTISH = Regex("^[a-zA-Z0-9\\-._~%]+(\\.[a-zA-Z0-9\\-_]{2,})+(:[0-9]{1,5})?([/?#].*)?$")
    private val LOCALHOST = Regex("^localhost(:[0-9]{1,5})?([/?#].*)?$", RegexOption.IGNORE_CASE)

    /**
     * What the address bar should load for [input]: a web address over HTTPS (a typed `http://`
     * is upgraded, the browser never speaks plain HTTP), or a search. Other schemes (`file:`,
     * `javascript:`, `intent:`) return null: typing them must never run anything.
     */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (OPAQUE_SCHEME.containsMatchIn(text)) return null
        if (EXPLICIT_SCHEME.containsMatchIn(text)) {
            val lower = text.lowercase()
            return when {
                lower.startsWith("https://") -> text
                lower.startsWith("http://") -> "https://" + text.substring(7)
                else -> null
            }
        }
        if (text.none { it.isWhitespace() } && (HOSTISH.matches(text) || LOCALHOST.matches(text))) {
            return "https://$text"
        }
        return SEARCH_URL + URLEncoder.encode(text, "UTF-8")
    }

    /** True for the only pages the browser loads. */
    fun isWebUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://")
    }

    private val TRACKING_EXACT = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "igshid", "mc_eid", "mc_cid", "_hsenc", "_hsmi", "wickedid", "vero_id", "srsltid",
        "s_kwcid", "ef_id", "oly_enc_id", "oly_anon_id"
    )

    /** [url] without click-tracking parameters; everything else, including order, is untouched. */
    fun stripTracking(url: String): String {
        val hash = url.indexOf('#')
        val beforeHash = if (hash >= 0) url.substring(0, hash) else url
        val fragment = if (hash >= 0) url.substring(hash) else ""
        val q = beforeHash.indexOf('?')
        if (q < 0) return url
        val base = beforeHash.substring(0, q)
        val kept = beforeHash.substring(q + 1).split('&').filter { part ->
            if (part.isEmpty()) return@filter false
            val name = part.substringBefore('=').lowercase()
            !(name.startsWith("utm_") || name in TRACKING_EXACT)
        }
        return if (kept.isEmpty()) base + fragment else "$base?${kept.joinToString("&")}$fragment"
    }

    /** Short label for a page, shown when it has no title. */
    fun displayHost(url: String): String = Hosts.hostOf(url)?.removePrefix("www.") ?: url
}
