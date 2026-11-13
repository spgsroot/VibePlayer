package ru.spgsroot.peachy.manga

/**
 * Tag names as shown to the user. Search, filters, favorites and storage always keep the English name the API
 * uses; only the text drawn on screen is translated, and a tag without a translation shows its English name.
 */
object TagNames {
    private const val RUSSIAN = "ru"

    private val tags by lazy { parse(TAGS_RU) }
    private val categories by lazy { parse(CATEGORIES_RU) }
    private val languages by lazy { parse(LANGUAGES_RU) }

    /** [language] is a language code such as `ru` or `en`; only Russian has a dictionary. */
    fun display(type: String, name: String, language: String): String {
        if (language != RUSSIAN) return name
        val table = when (type) {
            "tag" -> tags
            "category" -> categories
            "language" -> languages
            else -> return name
        }
        return table[name] ?: name
    }

    fun display(tag: GalleryTag, language: String): String = display(tag.type, tag.name, language)

    internal fun parse(raw: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val parts = line.split('|')
            check(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) { "Bad dictionary line: $line" }
            check(result.put(parts[0], parts[1]) == null) { "Duplicate dictionary key: ${parts[0]}" }
        }
        return result
    }
}
