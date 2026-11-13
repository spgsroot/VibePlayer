package ru.spgsroot.peachy.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.spgsroot.peachy.data.repository.SettingsRepository
import ru.spgsroot.peachy.domain.model.DspConfig
import ru.spgsroot.peachy.domain.model.Settings
import ru.spgsroot.peachy.translate.ProviderSettingsStore
import ru.spgsroot.peachy.translate.TranslationClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context, settingsRepository: SettingsRepository): Settings {
        // Provide default settings - actual settings should be loaded from repository via StateFlow
        return Settings(
            timerMs = 0L,
            playbackSpeed = 1.0f,
            dspConfig = DspConfig(),
            autoLockTimeoutMs = 30000L
        )
    }

    @Provides
    @Singleton
    fun provideTranslationClient(
        @ApplicationContext context: Context,
        providerStore: ProviderSettingsStore
    ): TranslationClient {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        return TranslationClient(
            OkHttpClient(),
            { providerStore.settings.value },
            "Peachy/$version (+https://github.com/spgsroot/VibePlayer)"
        )
    }
}
