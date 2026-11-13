package ru.spgsroot.peachy.browser.adblock

/** Host helpers shared by the filter engine and the browser. */
object Hosts {
    // Second-level labels that make a three-label registrable domain (example.co.uk).
    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

    /** Lowercase host of [url] without port or credentials, or null when it has none. */
    fun hostOf(url: String): String? {
        val start = url.indexOf("://")
        if (start < 0) return null
        var end = url.length
        for (i in start + 3 until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') {
                end = i
                break
            }
        }
        var authority = url.substring(start + 3, end)
        authority = authority.substringAfterLast('@')
        if (authority.startsWith("[")) return authority.substringBefore(']').drop(1).lowercase().ifEmpty { null }
        return authority.substringBefore(':').lowercase().trimEnd('.').ifEmpty { null }
    }

    /** Approximate eTLD+1 without the public suffix list: right for the common cases. */
    fun registrable(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val last = labels.last()
        val second = labels[labels.size - 2]
        val take = if (last.length == 2 && second in SECOND_LEVEL) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    fun isThirdParty(requestHost: String, pageHost: String): Boolean =
        registrable(requestHost) != registrable(pageHost)

    /** [host] and each parent domain down to the registrable one: a.b.c.com, b.c.com, c.com. */
    fun suffixes(host: String): List<String> {
        val stop = registrable(host)
        val out = ArrayList<String>(4)
        var current = host
        while (true) {
            out += current
            if (current == stop) break
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
        return out
    }

    fun matchesDomain(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")
}
