package ru.spgsroot.peachy.translate

import ru.spgsroot.peachy.locale.LocaleManager

/** Resolves the reader's target choice and names the languages used by translation prompts. */
object TargetLanguage {
    private val supported = setOf(LocaleManager.LANGUAGE_RUSSIAN, LocaleManager.LANGUAGE_ENGLISH)
    val SOURCE_CODES = listOf("auto", "ja", "zh", "ko", "en")
    val TARGET_CODES = listOf("app", "ru", "en", "uk", "de", "es", "fr", "pt", "it", "pl", "tr", "zh", "ja", "ko")

    fun resolve(appLanguageCode: String, deviceLanguage: String, chosen: String = "app"): String {
        if (chosen != "app") return chosen
        val code = if (appLanguageCode == LocaleManager.LANGUAGE_SYSTEM) deviceLanguage else appLanguageCode
        return if (code in supported) code else LocaleManager.LANGUAGE_ENGLISH
    }

    fun englishName(code: String): String = when (code.lowercase()) {
        "ru" -> "Russian"
        "en" -> "English"
        "uk" -> "Ukrainian"
        "de" -> "German"
        "es" -> "Spanish"
        "fr" -> "French"
        "pt" -> "Portuguese"
        "it" -> "Italian"
        "pl" -> "Polish"
        "tr" -> "Turkish"
        "zh" -> "Chinese"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        else -> code
    }
}
