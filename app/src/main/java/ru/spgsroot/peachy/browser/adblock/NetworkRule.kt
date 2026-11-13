package ru.spgsroot.peachy.browser.adblock

/** Resource kinds a rule can be limited to. A request with an unknown kind is OTHER. */
object ResourceType {
    const val SCRIPT = 1
    const val IMAGE = 2
    const val STYLESHEET = 4
    const val XHR = 8
    const val SUBDOCUMENT = 16
    const val MEDIA = 32
    const val FONT = 64
    const val PING = 128
    const val OBJECT = 256
    const val WEBSOCKET = 512
    const val OTHER = 1024

    /** What a rule without type options applies to: everything but the page itself. */
    const val DEFAULT = SCRIPT or IMAGE or STYLESHEET or XHR or SUBDOCUMENT or MEDIA or FONT or
        PING or OBJECT or WEBSOCKET or OTHER

    internal val BY_OPTION = mapOf(
        "script" to SCRIPT,
        "image" to IMAGE,
        "stylesheet" to STYLESHEET,
        "css" to STYLESHEET,
        "xmlhttprequest" to XHR,
        "xhr" to XHR,
        "subdocument" to SUBDOCUMENT,
        "frame" to SUBDOCUMENT,
        "media" to MEDIA,
        "font" to FONT,
        "ping" to PING,
        "object" to OBJECT,
        "websocket" to WEBSOCKET,
        "other" to OTHER
    )
}

/**
 * One parsed Adblock Plus network rule. Only syntax this engine implements exactly is kept:
 * anything else (regex rules, redirects, popup/document/csp options) is dropped at parse time,
 * because applying part of a rule would block pages the list author meant to allow.
 */
class NetworkRule(
    val pattern: String,
    val hostAnchor: Boolean,
    val startAnchor: Boolean,
    val endAnchor: Boolean,
    val exception: Boolean,
    val types: Int,
    /** 1 = third-party requests only, -1 = first-party only, 0 = either. */
    val party: Int,
    val includeDomains: List<String>,
    val excludeDomains: List<String>
) {
    /** The rarest-looking safe lookup key, or null when the rule must be checked for every URL. */
    val token: String? = pickToken(pattern, hostAnchor, startAnchor, endAnchor)

    fun appliesTo(type: Int, thirdParty: Boolean, pageHost: String?): Boolean {
        if (types and type == 0) return false
        if (party == 1 && !thirdParty) return false
        if (party == -1 && thirdParty) return false
        if (includeDomains.isNotEmpty() || excludeDomains.isNotEmpty()) {
            if (pageHost == null) return false
            if (excludeDomains.any { Hosts.matchesDomain(pageHost, it) }) return false
            if (includeDomains.isNotEmpty() && includeDomains.none { Hosts.matchesDomain(pageHost, it) }) return false
        }
        return true
    }

    /** [url] must already be lowercase. */
    fun matchesUrl(url: String): Boolean {
        if (hostAnchor) {
            val hostStart = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
            var hostEnd = url.length
            for (i in hostStart until url.length) {
                val c = url[i]
                if (c == '/' || c == '?' || c == '#' || c == ':') {
                    hostEnd = i
                    break
                }
            }
            var i = hostStart
            while (i <= hostEnd) {
                if (i == hostStart || url[i - 1] == '.') {
                    if (matchFrom(url, i, pattern, 0, endAnchor)) return true
                }
                i++
            }
            return false
        }
        if (startAnchor) return matchFrom(url, 0, pattern, 0, endAnchor)
        for (start in 0..url.length) {
            if (matchFrom(url, start, pattern, 0, endAnchor)) return true
        }
        return false
    }

    companion object {
        private val KNOWN_OPTIONS = ResourceType.BY_OPTION.keys + setOf(
            "third-party", "3p", "first-party", "1p", "domain", "match-case"
        )
        private val OPTION_SHAPE = Regex("^[a-z0-9~_,=|.\\-]+$")

        /** Null for comments, cosmetic rules and anything this engine does not implement. */
        fun parse(line: String): NetworkRule? {
            var s = line.trim()
            if (s.isEmpty() || s[0] == '!' || s[0] == '[') return null
            if (s.contains("##") || s.contains("#@#") || s.contains("#?#") || s.contains("#\$#")) return null
            val exception = s.startsWith("@@")
            if (exception) s = s.substring(2)

            var types = 0
            var negated = 0
            var party = 0
            val include = ArrayList<String>()
            val exclude = ArrayList<String>()
            val dollar = s.lastIndexOf('$')
            if (dollar >= 0) {
                val raw = s.substring(dollar + 1).lowercase()
                if (OPTION_SHAPE.matches(raw)) {
                    s = s.substring(0, dollar)
                    for (part in raw.split(',')) {
                        val negate = part.startsWith("~")
                        val name = part.removePrefix("~").substringBefore('=')
                        if (name !in KNOWN_OPTIONS) return null
                        when (name) {
                            "third-party", "3p" -> party = if (negate) -1 else 1
                            "first-party", "1p" -> party = if (negate) 1 else -1
                            "domain" -> {
                                if (negate) return null
                                for (d in part.substringAfter('=').split('|')) {
                                    if (d.isEmpty()) return null
                                    if (d.startsWith("~")) exclude += d.substring(1) else include += d
                                }
                            }
                            "match-case" -> Unit
                            else -> {
                                val bit = ResourceType.BY_OPTION.getValue(name)
                                if (negate) negated = negated or bit else types = types or bit
                            }
                        }
                    }
                }
            }
            val mask = (if (types != 0) types else ResourceType.DEFAULT) and negated.inv()

            // `/.../` is a regular expression in Adblock Plus syntax.
            if (s.length > 2 && s.startsWith("/") && s.endsWith("/")) return null

            var hostAnchor = false
            var startAnchor = false
            if (s.startsWith("||")) {
                hostAnchor = true
                s = s.substring(2)
            } else if (s.startsWith("|")) {
                startAnchor = true
                s = s.substring(1)
            }
            var endAnchor = false
            if (s.endsWith("|")) {
                endAnchor = true
                s = s.dropLast(1)
            }
            s = s.trim('*')
            val pattern = s.lowercase()
            if (pattern.isEmpty() && include.isEmpty() && party == 0 && !hostAnchor) return null
            if (pattern.any { it == ' ' }) return null
            return NetworkRule(pattern, hostAnchor, startAnchor, endAnchor, exception, mask, party, include, exclude)
        }

        /** Alphanumeric runs of a lowercase URL: the keys rules are looked up by. */
        fun urlTokens(url: String): List<String> {
            val out = ArrayList<String>(16)
            var start = -1
            for (i in 0..url.length) {
                val alnum = i < url.length && isAlnum(url[i])
                if (alnum) {
                    if (start < 0) start = i
                } else if (start >= 0) {
                    out += url.substring(start, i)
                    start = -1
                }
            }
            return out
        }

        private fun isAlnum(c: Char) = c in 'a'..'z' || c in '0'..'9'

        private fun pickToken(pattern: String, hostAnchor: Boolean, startAnchor: Boolean, endAnchor: Boolean): String? {
            var best: String? = null
            var i = 0
            while (i < pattern.length) {
                if (!isAlnum(pattern[i])) {
                    i++
                    continue
                }
                var j = i
                while (j < pattern.length && isAlnum(pattern[j])) j++
                val leftOk = if (i == 0) hostAnchor || startAnchor else pattern[i - 1] != '*'
                val rightOk = if (j == pattern.length) endAnchor else pattern[j] != '*'
                if (leftOk && rightOk && j - i >= 3 && (best == null || j - i > best.length)) {
                    best = pattern.substring(i, j)
                }
                i = j
            }
            return best
        }

        private fun isSeparator(c: Char) = !(c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-' || c == '.' || c == '%')

        private fun matchFrom(text: String, textStart: Int, pat: String, patStart: Int, endAnchor: Boolean): Boolean {
            var ti = textStart
            var pi = patStart
            while (pi < pat.length) {
                val c = pat[pi]
                if (c == '*') {
                    while (pi < pat.length && pat[pi] == '*') pi++
                    if (pi == pat.length) return true
                    for (k in ti..text.length) {
                        if (matchFrom(text, k, pat, pi, endAnchor)) return true
                    }
                    return false
                }
                if (c == '^') {
                    if (ti == text.length) return pi == pat.length - 1
                    if (!isSeparator(text[ti])) return false
                    ti++
                    pi++
                    continue
                }
                if (ti >= text.length || text[ti] != c) return false
                ti++
                pi++
            }
            return !endAnchor || ti == text.length
        }
    }
}
