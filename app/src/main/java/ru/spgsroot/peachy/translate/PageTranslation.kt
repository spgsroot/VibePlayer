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
data class TranslatedLine(
    val kind: LineKind,
    val text: String,
    val translation: String,
    val box: Box? = null
)

/** What the model made of one page, in reading order. An empty [lines] means the page has no text. */
data class PageTranslation(val sourceLanguage: String, val lines: List<TranslatedLine>) {
    fun toJson(): String = JSONObject()
        .put("sourceLanguage", sourceLanguage)
        .put("lines", JSONArray(lines.map { line ->
            JSONObject().put("kind", line.kind.wire).put("text", line.text).put("translation", line.translation)
                .apply {
                    line.box?.let { box ->
                        put("box", JSONArray()
                            .put(box.left.toDouble()).put(box.top.toDouble())
                            .put(box.right.toDouble()).put(box.bottom.toDouble()))
                    }
                }
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
                        add(TranslatedLine(
                            LineKind.fromWire(item.optString("kind")),
                            item.optString("text").trim(),
                            translation,
                            parseBox(item.optJSONArray("box"))
                        ))
                    }
                }
                PageTranslation(root.optString("sourceLanguage"), lines)
            } catch (e: JSONException) {
                null
            }
        }

        /** A bad rectangle never invalidates the text it belongs to. Stored rectangles use fractions. */
        private fun parseBox(box: JSONArray?): Box? {
            if (box == null || box.length() != 4) return null
            val x1 = (box.opt(0) as? Number)?.toDouble() ?: return null
            val y1 = (box.opt(1) as? Number)?.toDouble() ?: return null
            val x2 = (box.opt(2) as? Number)?.toDouble() ?: return null
            val y2 = (box.opt(3) as? Number)?.toDouble() ?: return null
            if (!x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite()) return null
            val scale = if (x1 > 1.0 || y1 > 1.0 || x2 > 1.0 || y2 > 1.0) 1000.0 else 1.0
            val firstX = (x1 / scale).coerceIn(0.0, 1.0).toFloat()
            val firstY = (y1 / scale).coerceIn(0.0, 1.0).toFloat()
            val secondX = (x2 / scale).coerceIn(0.0, 1.0).toFloat()
            val secondY = (y2 / scale).coerceIn(0.0, 1.0).toFloat()
            val left = minOf(firstX, secondX)
            val top = minOf(firstY, secondY)
            val right = maxOf(firstX, secondX)
            val bottom = maxOf(firstY, secondY)
            return if (left < right && top < bottom) Box(left, top, right, bottom) else null
        }
    }
}
