package ru.spgsroot.vibeplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.spgsroot.vibeplayer.data.db.PlaybackStateDao
import ru.spgsroot.vibeplayer.data.db.SettingsDao
import ru.spgsroot.vibeplayer.data.db.VideoDao
import ru.spgsroot.vibeplayer.data.repository.PlaybackStateRepository
import ru.spgsroot.vibeplayer.data.repository.SettingsRepository
import ru.spgsroot.vibeplayer.data.repository.VideoRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideVideoRepository(videoDao: VideoDao): VideoRepository {
        return VideoRepository(videoDao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(settingsDao: SettingsDao): SettingsRepository {
        return SettingsRepository(settingsDao)
    }

    @Provides
    @Singleton
    fun providePlaybackStateRepository(playbackStateDao: PlaybackStateDao): PlaybackStateRepository {
        return PlaybackStateRepository(playbackStateDao)
    }
}
