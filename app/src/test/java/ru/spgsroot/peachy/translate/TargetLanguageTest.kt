package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetLanguageTest {
    @Test
    fun chosenAppLanguageWinsOverTheDevice() {
        assertEquals("ru", TargetLanguage.resolve("ru", "en"))
        assertEquals("en", TargetLanguage.resolve("en", "ru"))
    }

    @Test
    fun systemLanguageFollowsTheDeviceWhenSupported() {
        assertEquals("ru", TargetLanguage.resolve("system", "ru"))
        assertEquals("en", TargetLanguage.resolve("system", "en"))
    }

    @Test
    fun systemLanguageThatTheAppDoesNotKnowIsEnglish() {
        assertEquals("en", TargetLanguage.resolve("system", "de"))
    }

    @Test
    fun followingAppKeepsExistingFallbackBehavior() {
        assertEquals("ru", TargetLanguage.resolve("ru", "en", "app"))
        assertEquals("en", TargetLanguage.resolve("en", "ru", "app"))
        assertEquals("ru", TargetLanguage.resolve("system", "ru", "app"))
        assertEquals("en", TargetLanguage.resolve("system", "de", "app"))
        assertEquals("en", TargetLanguage.resolve("de", "ru", "app"))
    }

    @Test
    fun explicitTargetWinsOverAppAndDeviceLanguages() {
        assertEquals("ja", TargetLanguage.resolve("ru", "en", "ja"))
        assertEquals("de", TargetLanguage.resolve("system", "ru", "de"))
        assertEquals("ru", TargetLanguage.resolve("en", "en", "ru"))
    }

    @Test
    fun explicitUnknownCodeIsNotRestrictedToAppLanguages() {
        assertEquals("xx", TargetLanguage.resolve("ru", "en", "xx"))
        assertEquals("pt-BR", TargetLanguage.resolve("system", "ru", "pt-BR"))
    }

    @Test
    fun languageChoicesExposeSupportedSourcesAndTargetsInDisplayOrder() {
        assertEquals(listOf("auto", "ja", "zh", "ko", "en"), TargetLanguage.SOURCE_CODES)
        assertEquals(
            listOf("app", "ru", "en", "uk", "de", "es", "fr", "pt", "it", "pl", "tr", "zh", "ja", "ko"),
            TargetLanguage.TARGET_CODES
        )
    }

    @Test
    fun promptsReceiveEnglishNamesForEverySelectableLanguage() {
        val names = mapOf(
            "ru" to "Russian", "en" to "English", "uk" to "Ukrainian", "de" to "German",
            "es" to "Spanish", "fr" to "French", "pt" to "Portuguese", "it" to "Italian",
            "pl" to "Polish", "tr" to "Turkish", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean"
        )
        for ((code, expected) in names) {
            assertEquals(expected, TargetLanguage.englishName(code))
            assertEquals(expected, TargetLanguage.englishName(code.uppercase(java.util.Locale.ROOT)))
        }
    }

    @Test
    fun unknownPromptLanguageFallsBackToTheUnmodifiedCode() {
        assertEquals("xx", TargetLanguage.englishName("xx"))
        assertEquals("pt-BR", TargetLanguage.englishName("pt-BR"))
    }
}
