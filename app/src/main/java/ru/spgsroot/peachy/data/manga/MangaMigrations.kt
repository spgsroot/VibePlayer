package ru.spgsroot.peachy.data.manga

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object MangaMigrations {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS read_history (galleryId INTEGER NOT NULL PRIMARY KEY, " +
                    "title TEXT NOT NULL, thumbnailPath TEXT NOT NULL, page INTEGER NOT NULL, " +
                    "pageCount INTEGER NOT NULL, readAt INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS search_history (query TEXT NOT NULL PRIMARY KEY, usedAt INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS tag_blacklist (kind TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(kind, value))"
            )
        }
    }

    /** Adds the table that remembers what each opened gallery looked like; nothing else is touched. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS gallery_memory (galleryId INTEGER NOT NULL PRIMARY KEY, " +
                    "title TEXT NOT NULL, thumbnailPath TEXT NOT NULL, numPages INTEGER NOT NULL, " +
                    "tags TEXT NOT NULL, uploadedAt INTEGER, numFavorites INTEGER, " +
                    "seenAt INTEGER NOT NULL, dismissed INTEGER NOT NULL)"
            )
        }
    }

    /** Adds the table of pages saved one by one from the reader; nothing else is touched. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS saved_pages (galleryId INTEGER NOT NULL, page INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, fileName TEXT NOT NULL, savedAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(galleryId, page))"
            )
        }
    }

    /** Adds the cache of page translations; nothing else is touched. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS page_translations (galleryId INTEGER NOT NULL, page INTEGER NOT NULL, " +
                    "lang TEXT NOT NULL, model TEXT NOT NULL, json TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(galleryId, page, lang))"
            )
        }
    }
}
