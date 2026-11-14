package ru.spgsroot.peachy.translate

/** How the page's original text is read before translation. Wires are persisted in reader settings and cache keys. */
enum class TranslateEngine(val wire: String) {
    Model("model"), MlKit("mlkit");

    companion object {
        fun fromWire(value: String?): TranslateEngine = entries.firstOrNull { it.wire == value } ?: Model
    }
}

/** The selected engine and source language, with a resolved target language rather than the "app" preference. */
data class TranslateRequest(
    val engine: TranslateEngine = TranslateEngine.Model,
    val source: String = "auto",
    val target: String
)
