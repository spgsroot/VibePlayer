package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationKeyTest {
    @Test
    fun defaultEngineAndAutomaticSourceKeepLegacyLanguageKeys() {
        assertEquals("ru", TranslationKey.of(TranslateEngine.Model, "auto", "ru"))
        assertEquals("en", TranslationKey.of(TranslateEngine.Model, "auto", "en"))
        assertEquals("xx", TranslationKey.of(TranslateEngine.Model, "auto", "xx"))
    }

    @Test
    fun nonDefaultKeysIncludeTargetStableEngineWireAndSource() {
        val cases = listOf(
            Triple(TranslateEngine.Model, "ja", "ru") to "ru|model|ja",
            Triple(TranslateEngine.Model, "en", "de") to "de|model|en",
            Triple(TranslateEngine.MlKit, "auto", "ru") to "ru|mlkit|auto",
            Triple(TranslateEngine.MlKit, "ja", "ru") to "ru|mlkit|ja",
            Triple(TranslateEngine.MlKit, "zh", "en") to "en|mlkit|zh"
        )
        for ((options, expected) in cases) {
            assertEquals(expected, TranslationKey.of(options.first, options.second, options.third))
            assertEquals(expected, TranslationKey.of(options.first, options.second, options.third))
        }
    }

    @Test
    fun everySelectableEngineSourceAndResolvedTargetCombinationIsDistinct() {
        val keys = mutableSetOf<String>()
        val sources = listOf("auto", "ja", "zh", "ko", "en")
        val targets = listOf("ru", "en", "uk", "de", "es", "fr", "pt", "it", "pl", "tr", "zh", "ja", "ko")
        for (engine in TranslateEngine.entries) {
            for (source in sources) {
                for (target in targets) {
                    keys.add(TranslationKey.of(engine, source, target))
                }
            }
        }
        assertEquals(130, keys.size)
    }

    @Test
    fun opaqueKeysPreserveUnknownLanguageCodesWithoutNormalization() {
        assertEquals("pt-BR", TranslationKey.of(TranslateEngine.Model, "auto", "pt-BR"))
        assertEquals("xx|model|xx-Script", TranslationKey.of(TranslateEngine.Model, "xx-Script", "xx"))
        assertEquals("pt-BR|mlkit|AUTO", TranslationKey.of(TranslateEngine.MlKit, "AUTO", "pt-BR"))
    }

    @Test
    fun engineWiresRemainCompatibleWithPersistedKeys() {
        assertEquals("model", TranslateEngine.Model.wire)
        assertEquals("mlkit", TranslateEngine.MlKit.wire)
    }
}
