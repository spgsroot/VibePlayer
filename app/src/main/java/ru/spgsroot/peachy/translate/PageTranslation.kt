package ru.spgsroot.peachy.translate

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class LineKind(val wire: String) {
    Speech("speech"), Thought("thought"), Narration("narration"), Sfx("sfx"), Sign("sign");

    companion object {
        fun fromWire(value: String?): LineKind = entries.firstOrNull { it.wire == value } ?: Speech
    }
}

/** One piece of text on a page: what it says, what it means in the reader's language, and what kind it is. */
data class TranslatedLine(val kind: LineKind, val text: String, val translation: String)

/** What the model made of one page, in reading order. An empty [lines] means the page has no text. */
data class PageTranslation(val sourceLanguage: String, val lines: List<TranslatedLine>) {
    fun toJson(): String = JSONObject()
        .put("sourceLanguage", sourceLanguage)
        .put("lines", JSONArray(lines.map {
            JSONObject().put("kind", it.kind.wire).put("text", it.text).put("translation", it.translation)
        }))
        .toString()

    companion object {
        /**
         * Reads a model reply or a stored [toJson] string. The reply may be wrapped in a Markdown fence or
         * prose; the outermost `{...}` is taken. Null when nothing usable is there (including a cut-off reply).
         */
        fun parse(raw: String): PageTranslation? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                val root = JSONObject(raw.substring(start, end + 1))
                val items = root.getJSONArray("lines")
                val lines = buildList {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val translation = item.optString("translation").trim()
                        if (translation.isEmpty()) continue
                        add(TranslatedLine(LineKind.fromWire(item.optString("kind")), item.optString("text").trim(), translation))
                    }
                }
                PageTranslation(root.optString("sourceLanguage"), lines)
            } catch (e: JSONException) {
                null
            }
        }
    }
}
