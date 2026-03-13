package ru.spgsroot.vibeplayer.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Manages app locale settings.
 * Supports: Russian, English, and System default.
 */
object LocaleManager {
    
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_SYSTEM = "system"
    
    private const val PREF_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language_code"
    
    private var currentLocale: String = LANGUAGE_SYSTEM
    
    fun setLocale(context: Context, languageCode: String): Context {
        currentLocale = languageCode
        saveLocale(context, languageCode)
        return updateContextLocale(context, languageCode)
    }
    
    fun updateContextLocale(context: Context, languageCode: String): Context {
        val locale = getLocale(languageCode)
        return context.createConfigurationContext(createConfiguration(locale))
    }
    
    fun getLocale(languageCode: String): Locale {
        return when (languageCode) {
            LANGUAGE_RUSSIAN -> Locale("ru")
            LANGUAGE_ENGLISH -> Locale("en")
            LANGUAGE_SYSTEM -> Locale.getDefault()
            else -> Locale.getDefault()
        }
    }
    
    private fun createConfiguration(locale: Locale): Configuration {
        val configuration = Configuration()
        configuration.setLocale(locale)
        val locales = LocaleListCompat.create(locale)
        ConfigurationCompat.setLocales(configuration, locales)
        return configuration
    }
    
    fun getCurrentLocale(): String = currentLocale
    
    fun isSystemLocale(): Boolean = currentLocale == LANGUAGE_SYSTEM
    
    fun getSavedLocale(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
    }
    
    fun saveLocale(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }
    
    fun initialize(context: Context) {
        currentLocale = getSavedLocale(context)
    }
}
