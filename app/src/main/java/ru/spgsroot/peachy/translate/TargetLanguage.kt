package ru.spgsroot.peachy.translate

import ru.spgsroot.peachy.locale.LocaleManager

/** The language a page is translated into: the app's own, or the device's when the app follows the system. */
object TargetLanguage {
    private val supported = setOf(LocaleManager.LANGUAGE_RUSSIAN, LocaleManager.LANGUAGE_ENGLISH)

    fun resolve(appLanguageCode: String, deviceLanguage: String): String {
        val code = if (appLanguageCode == LocaleManager.LANGUAGE_SYSTEM) deviceLanguage else appLanguageCode
        return if (code in supported) code else LocaleManager.LANGUAGE_ENGLISH
    }
}
