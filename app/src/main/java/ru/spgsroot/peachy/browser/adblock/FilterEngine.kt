package ru.spgsroot.peachy.browser.adblock

/**
 * Decides whether a request is an ad or a tracker, and which elements a page should hide.
 * Immutable after [Builder.build], so it can be read from WebView's request threads while a
 * new one is being built from an updated list.
 */
class FilterEngine private constructor(
    private val blocks: Index,
    private val allows: Index,
    private val cosmetic: CosmeticRules,
    val networkRuleCount: Int
) {
    val cosmeticRuleCount: Int get() = cosmetic.ruleCount

    /** [pageHost] is the host of the page that made the request; null for unknown. */
    fun shouldBlock(url: String, pageHost: String?, type: Int): Boolean {
        if (blocks.isEmpty()) return false
        val lower = url.lowercase()
        val host = Hosts.hostOf(lower) ?: return false
        val thirdParty = pageHost != null && Hosts.isThirdParty(host, pageHost)
        val tokens = NetworkRule.urlTokens(lower)
        if (!blocks.matches(lower, tokens, type, thirdParty, pageHost)) return false
        return !allows.matches(lower, tokens, type, thirdParty, pageHost)
    }

    /** CSS hiding the page's ad containers, or "" when none apply. */
    fun cosmeticCss(pageHost: String): String = cosmetic.cssFor(pageHost)

    private class Index {
        private val byToken = HashMap<String, ArrayList<NetworkRule>>()
        private val untokenised = ArrayList<NetworkRule>()

        fun isEmpty() = byToken.isEmpty() && untokenised.isEmpty()

        fun add(rule: NetworkRule) {
            val token = rule.token
            if (token == null) untokenised += rule else byToken.getOrPut(token) { ArrayList(2) } += rule
        }

        fun matches(url: String, tokens: List<String>, type: Int, thirdParty: Boolean, pageHost: String?): Boolean {
            for (token in tokens) {
                val rules = byToken[token] ?: continue
                for (rule in rules) {
                    if (rule.appliesTo(type, thirdParty, pageHost) && rule.matchesUrl(url)) return true
                }
            }
            for (rule in untokenised) {
                if (rule.appliesTo(type, thirdParty, pageHost) && rule.matchesUrl(url)) return true
            }
            return false
        }
    }

    class Builder {
        private val blocks = Index()
        private val allows = Index()
        private val cosmetic = CosmeticRules.Builder()
        private var count = 0

        /** Adds every rule of one Adblock Plus list; lines this engine cannot apply are skipped. */
        fun addList(text: CharSequence): Builder {
            var start = 0
            val n = text.length
            while (start <= n) {
                var end = start
                while (end < n && text[end] != '\n') end++
                if (end > start) addLine(text.subSequence(start, end).toString())
                start = end + 1
            }
            return this
        }

        fun addLine(line: String): Builder {
            val rule = NetworkRule.parse(line)
            if (rule != null) {
                if (rule.exception) allows.add(rule) else blocks.add(rule)
                count++
            } else {
                cosmetic.add(CosmeticParser.parse(line))
            }
            return this
        }

        fun build() = FilterEngine(blocks, allows, cosmetic.build(), count)
    }

    companion object {
        val EMPTY: FilterEngine = Builder().build()
    }
}
