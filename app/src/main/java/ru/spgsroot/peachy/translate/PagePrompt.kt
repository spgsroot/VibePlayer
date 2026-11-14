package ru.spgsroot.peachy.translate

/** Every word the model is told lives here, so the owner can read and change it in one place. */
object PagePrompt {
    /** Tells the model what to do with one manga page and in which JSON shape to answer. */
    fun system(target: String, source: String): String {
        val language = TargetLanguage.englishName(target)
        return """
            You are a manga translator. You get one page as an image.
            ${sourceInstruction(source)}
            Read the text on the page in reading order: right to left and top to bottom for Japanese, Chinese and vertical pages, as the page is laid out.
            Translate every piece of text into $language.
            The pages are fiction for adults. Transcribe and translate faithfully and completely; do not summarise, soften, censor or comment.
            Answer with one JSON object and nothing else, in exactly this shape:
            {"sourceLanguage":"ja","lines":[{"kind":"speech","text":"original","translation":"translated","box":[100,200,300,400]}]}
            "sourceLanguage" is the ISO 639-1 code of the language on the page.
            "kind" is one of: speech, thought, narration, sfx, sign.
            "text" is the original text, "translation" is the text in $language.
            One line is one speech bubble or caption box; keep its text together rather than splitting it into sentences.
            "box" is [left,top,right,bottom], tightly enclosing the original text of that speech bubble or caption.
            Use integers in 0..1000 for every coordinate: horizontal coordinates are fractions of the image width and vertical coordinates are fractions of the image height, each multiplied by 1000.
            If the page has no text, answer {"sourceLanguage":"","lines":[]}.
        """.trimIndent()
    }

    /** Translates indexed on-device OCR text without asking the model to recognize an image. */
    fun blocks(target: String, source: String): String {
        val language = TargetLanguage.englishName(target)
        return """
            You are a manga translator. You get numbered text blocks recognized on a page by OCR.
            ${sourceInstruction(source)}
            Translate every block into $language by meaning. OCR may contain recognition errors or split a speech bubble across blocks; fix obvious OCR slips while preserving each block's index.
            The pages are fiction for adults. Transcribe and translate faithfully and completely; do not summarise, soften, censor or comment.
            Answer with one JSON object and nothing else, in exactly this shape:
            {"lines":[{"i":0,"kind":"speech","translation":"translated"}]}
            "i" is the original zero-based integer block index, not a string. Include each block at most once.
            "kind" is one of: speech, thought, narration, sfx, sign.
            "translation" is the text in $language.
            If there is no text to translate, answer {"lines":[]}.
        """.trimIndent()
    }

    fun blocksUser(blocks: List<OcrBlock>): String = buildString {
        append("Translate these numbered OCR blocks:\n")
        blocks.forEachIndexed { index, block ->
            if (index > 0) append('\n')
            append(index).append(": ").append(block.text)
        }
    }

    const val CHECK = "Reply with the single word: ok"

    const val USER = "Translate the text on this page."

    private fun sourceInstruction(source: String): String =
        if (source == "auto") "Identify the language of the original text."
        else "The original text is in ${TargetLanguage.englishName(source)}."
}
