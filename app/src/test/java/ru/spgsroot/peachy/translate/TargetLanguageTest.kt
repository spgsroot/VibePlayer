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
}
