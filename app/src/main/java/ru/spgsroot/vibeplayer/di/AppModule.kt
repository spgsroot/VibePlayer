package ru.spgsroot.vibeplayer.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.spgsroot.vibeplayer.data.repository.SettingsRepository
import ru.spgsroot.vibeplayer.domain.model.DspConfig
import ru.spgsroot.vibeplayer.domain.model.Settings
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
}
