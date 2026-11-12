package ru.spgsroot.peachy.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.spgsroot.peachy.data.db.PlaybackStateDao
import ru.spgsroot.peachy.data.db.SettingsDao
import ru.spgsroot.peachy.data.db.VideoDao
import ru.spgsroot.peachy.data.repository.PlaybackStateRepository
import ru.spgsroot.peachy.data.repository.SettingsRepository
import ru.spgsroot.peachy.data.repository.VideoRepository
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
