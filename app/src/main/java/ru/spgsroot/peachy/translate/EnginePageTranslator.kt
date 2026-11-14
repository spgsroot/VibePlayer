package ru.spgsroot.peachy.translate

import javax.inject.Inject

/** Selects image recognition by the provider or on-device OCR followed by text-only translation. */
class EnginePageTranslator @Inject constructor(
    private val client: TranslationClient,
    private val ocr: PageOcr
) : PageTranslator {
    override suspend fun translate(galleryId: Int, jpeg: ByteArray, request: TranslateRequest): PageTranslation =
        when (request.engine) {
            TranslateEngine.Model -> client.translate(galleryId, jpeg, request)
            TranslateEngine.MlKit -> {
                val blocks = ocr.recognize(jpeg, request.source)
                val source = if (request.source == "auto") "" else request.source
                if (blocks.isEmpty()) {
                    PageTranslation(source, emptyList())
                } else {
                    val translations = client.translateBlocks(galleryId, blocks, request.source, request.target)
                    val accepted = HashSet<Int>()
                    val lines = buildList {
                        for (translation in translations) {
                            val block = blocks.getOrNull(translation.index) ?: continue
                            if (!accepted.add(translation.index)) continue
                            add(TranslatedLine(translation.kind, block.text, translation.translation, block.box))
                        }
                    }
                    PageTranslation(source, lines)
                }
            }
        }
}
