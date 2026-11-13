package ru.spgsroot.peachy.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.spgsroot.peachy.data.db.*
import ru.spgsroot.peachy.data.notes.NoteDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = KeystoreManager.getOrCreateKey(context)
        return AppDatabase.create(context, passphrase)
    }

    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao = database.videoDao()

    @Provides
    fun provideSettingsDao(database: AppDatabase): SettingsDao = database.settingsDao()

    @Provides
    fun providePlaybackStateDao(database: AppDatabase): PlaybackStateDao = database.playbackStateDao()
    @Provides
    fun provideLibraryGalleryDao(database: AppDatabase): LibraryGalleryDao = database.libraryGalleryDao()

    @Provides
    fun provideReadHistoryDao(database: AppDatabase): ReadHistoryDao = database.readHistoryDao()

    @Provides
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideTagBlacklistDao(database: AppDatabase): TagBlacklistDao = database.tagBlacklistDao()

    @Provides
    fun provideGalleryMemoryDao(database: AppDatabase): GalleryMemoryDao = database.galleryMemoryDao()

    @Provides
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideBrowserHistoryDao(database: AppDatabase): BrowserHistoryDao = database.browserHistoryDao()

    @Provides
    fun provideAllowlistDao(database: AppDatabase): AllowlistDao = database.allowlistDao()

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao = database.noteDao()
}
