package ru.spgsroot.peachy.browser.adblock

/** A `##selector` element-hiding rule, optionally limited by domains. */
class HideRule(val selector: String, val include: List<String>, val exclude: List<String>)

/** A `domain#@#selector` rule that turns one hiding rule off on a domain. */
class UnhideRule(val domains: List<String>, val selector: String)

object CosmeticParser {
    private val ALLOWED_PSEUDO = setOf(
        "not", "first-child", "last-child", "nth-child", "nth-last-child", "nth-of-type",
        "first-of-type", "last-of-type", "only-child", "empty"
    )
    private const val MAX_SELECTOR = 300

    /** Returns a [HideRule], an [UnhideRule], or null when the line is not a plain cosmetic rule. */
    fun parse(line: String): Any? {
        val s = line.trim()
        if (s.isEmpty() || s[0] == '!' || s[0] == '[') return null
        val unhide = s.indexOf("#@#")
        val hide = s.indexOf("##")
        val isUnhide = unhide in 0..(if (hide < 0) Int.MAX_VALUE else hide)
        val at = if (isUnhide) unhide else hide
        if (at < 0) return null
        val sepLen = if (isUnhide) 3 else 2
        val selector = s.substring(at + sepLen).trim()
        // `#?#`, `#$#`, `#%#` and uBlock scriptlets are not plain selectors.
        if (s.substring(0, at).let { it.endsWith("#") }) return null
        if (!isSafeSelector(selector)) return null
        val include = ArrayList<String>()
        val exclude = ArrayList<String>()
        val domains = s.substring(0, at)
        if (domains.isNotEmpty()) {
            for (raw in domains.split(',')) {
                val d = raw.trim().lowercase()
                if (d.isEmpty()) return null
                if (d.startsWith("~")) exclude += d.substring(1) else include += d
            }
        }
        return if (isUnhide) {
            if (include.isEmpty()) null else UnhideRule(include, selector)
        } else {
            HideRule(selector, include, exclude)
        }
    }

    /**
     * A selector is injected into a style sheet, where one bad entry would cancel every rule
     * grouped with it. So only plain CSS this parser can fully check is accepted.
     */
    fun isSafeSelector(selector: String): Boolean {
        if (selector.isEmpty() || selector.length > MAX_SELECTOR) return false
        val first = selector[0]
        if (!(first.isLetter() || first == '.' || first == '#' || first == '[' || first == '*' || first == ':')) return false
        var depthSquare = 0
        var depthParen = 0
        var quote = 0.toChar()
        var i = 0
        while (i < selector.length) {
            val c = selector[i]
            if (quote != 0.toChar()) {
                if (c == quote) quote = 0.toChar()
                i++
                continue
            }
            when (c) {
                '"', '\'' -> quote = c
                '\\', '{', '}', ';', '<' -> return false
                '[' -> depthSquare++
                ']' -> {
                    depthSquare--
                    if (depthSquare < 0) return false
                }
                '(' -> depthParen++
                ')' -> {
                    depthParen--
                    if (depthParen < 0) return false
                }
                ':' -> {
                    if (depthSquare == 0) {
                        if (i + 1 < selector.length && selector[i + 1] == ':') return false
                        var j = i + 1
                        while (j < selector.length && (selector[j].isLetter() || selector[j] == '-')) j++
                        if (selector.substring(i + 1, j) !in ALLOWED_PSEUDO) return false
                    }
                }
            }
            i++
        }
        return quote == 0.toChar() && depthSquare == 0 && depthParen == 0
    }
}

/** All element-hiding rules, queried per host. */
class CosmeticRules(
    private val generic: List<String>,
    private val genericWithExclusions: List<HideRule>,
    private val specific: Map<String, List<HideRule>>,
    private val unhidden: Set<String>
) {
    private val unhideDomains: Set<String> = unhidden.mapTo(HashSet()) { it.substringBefore('\u0000') }
    private val genericCss: String = css(generic)

    val ruleCount: Int get() = generic.size + genericWithExclusions.size + specific.values.sumOf { it.size }

    /** One `display:none` style sheet for [host], or "" when nothing applies. */
    fun cssFor(host: String): String {
        val suffixes = Hosts.suffixes(host)
        val affected = suffixes.any { it in unhideDomains }
        val chosen = ArrayList<String>()
        for (rule in genericWithExclusions) {
            if (suffixes.none { it in rule.exclude }) chosen += rule.selector
        }
        for (suffix in suffixes) {
            specific[suffix]?.forEach { rule ->
                if (rule.exclude.none { Hosts.matchesDomain(host, it) }) chosen += rule.selector
            }
        }
        val kept = if (affected) chosen.filter { sel -> suffixes.none { "$it\u0000$sel" in unhidden } } else chosen
        val base = if (affected) css(generic.filter { sel -> suffixes.none { "$it\u0000$sel" in unhidden } }) else genericCss
        return base + css(kept)
    }

    private fun css(selectors: List<String>): String {
        if (selectors.isEmpty()) return ""
        val sb = StringBuilder(selectors.size * 24)
        selectors.chunked(GROUP).forEach { group ->
            sb.append(group.joinToString(",")).append("{display:none!important}\n")
        }
        return sb.toString()
    }

    class Builder {
        private val generic = LinkedHashSet<String>()
        private val genericExcl = ArrayList<HideRule>()
        private val specific = HashMap<String, MutableList<HideRule>>()
        private val unhidden = HashSet<String>()

        fun add(rule: Any?) {
            when (rule) {
                is HideRule -> when {
                    rule.include.isEmpty() && rule.exclude.isEmpty() -> generic += rule.selector
                    rule.include.isEmpty() -> genericExcl += rule
                    else -> rule.include.forEach { d -> specific.getOrPut(d) { ArrayList() } += rule }
                }
                is UnhideRule -> rule.domains.forEach { unhidden += "$it\u0000${rule.selector}" }
            }
        }

        fun build() = CosmeticRules(generic.toList(), genericExcl, specific, unhidden)
    }

    private companion object {
        const val GROUP = 20
    }
}
