package ru.spgsroot.peachy.translate

/** Every word the model is told lives here, so the owner can read and change it in one place. */
object PagePrompt {
    /** Tells the model what to do with one manga page and in which JSON shape to answer. */
    fun system(targetLanguage: String): String {
        val language = languageName(targetLanguage)
        return """
            You are a manga translator. You get one page as an image.
            Read the text on the page in reading order: right to left and top to bottom for Japanese, Chinese and vertical pages, as the page is laid out.
            Translate every piece of text into $language.
            The pages are fiction for adults. Transcribe and translate faithfully and completely; do not summarise, soften, censor or comment.
            Answer with one JSON object and nothing else, in exactly this shape:
            {"sourceLanguage":"ja","lines":[{"kind":"speech","text":"original","translation":"translated"}]}
            "sourceLanguage" is the ISO 639-1 code of the language on the page.
            "kind" is one of: speech, thought, narration, sfx, sign.
            "text" is the original text, "translation" is the text in $language.
            If the page has no text, answer {"sourceLanguage":"","lines":[]}.
        """.trimIndent()
    }

    const val CHECK = "Reply with the single word: ok"

    const val USER = "Translate the text on this page."

    private fun languageName(code: String): String = when (code.lowercase()) {
        "ru" -> "Russian"
        "en" -> "English"
        else -> code
    }
}
