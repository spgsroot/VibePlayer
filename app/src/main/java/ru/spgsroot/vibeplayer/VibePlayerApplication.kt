package ru.spgsroot.vibeplayer

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.data.repository.SettingsRepository
import ru.spgsroot.vibeplayer.locale.LocaleManager
import javax.inject.Inject

@HiltAndroidApp
class VibePlayerApplication : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Initialize locale manager with saved locale
        LocaleManager.initialize(base)
    }

    override fun onCreate() {
        super.onCreate()
        initializeSettings()
    }

    private fun initializeSettings() {
        applicationScope.launch {
            settingsRepository.initializeDefaults()
            // Load saved language and apply it
            val settings = settingsRepository.getSettings().firstOrNull()
            val languageCode = settings?.languageCode ?: LocaleManager.getSavedLocale(this@VibePlayerApplication)
            LocaleManager.setLocale(this@VibePlayerApplication, languageCode)
        }
    }
}
