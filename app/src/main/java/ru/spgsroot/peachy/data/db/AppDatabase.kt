package ru.spgsroot.peachy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import ru.spgsroot.peachy.data.manga.MangaMigrations.MIGRATION_5_6
import ru.spgsroot.peachy.data.manga.MangaMigrations.MIGRATION_6_7
import ru.spgsroot.peachy.data.browser.BrowserMigrations.MIGRATION_7_8
import ru.spgsroot.peachy.data.notes.NoteDao
import ru.spgsroot.peachy.data.notes.NoteEntity
import ru.spgsroot.peachy.data.notes.NoteMigrations.MIGRATION_8_9

@Database(
    entities = [
        VideoEntity::class, SettingsEntity::class, PlaybackStateEntity::class, LibraryGalleryEntity::class,
        ReadHistoryEntity::class, SearchHistoryEntity::class, TagBlacklistEntity::class, GalleryMemoryEntity::class,
        BookmarkEntity::class, BrowserHistoryEntity::class, AllowlistEntity::class, NoteEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun settingsDao(): SettingsDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun libraryGalleryDao(): LibraryGalleryDao
    abstract fun readHistoryDao(): ReadHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun tagBlacklistDao(): TagBlacklistDao
    abstract fun galleryMemoryDao(): GalleryMemoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun allowlistDao(): AllowlistDao
    abstract fun noteDao(): NoteDao

    companion object {
        private const val DB_NAME = "peachy.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE settings ADD COLUMN languageCode TEXT NOT NULL DEFAULT 'system'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE settings ADD COLUMN dspPowerBoost REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE settings ADD COLUMN dspThreshold REAL NOT NULL DEFAULT 0.03")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS library_galleries (" +
                        "id INTEGER NOT NULL PRIMARY KEY, " +
                        "title TEXT NOT NULL, " +
                        "thumbnailPath TEXT NOT NULL, " +
                        "pagePaths TEXT NOT NULL, " +
                        "favorite INTEGER NOT NULL, " +
                        "addedAt INTEGER NOT NULL)"
                )
            }
        }


        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9
                )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
