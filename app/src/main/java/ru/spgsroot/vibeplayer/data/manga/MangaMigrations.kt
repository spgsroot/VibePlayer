package ru.spgsroot.vibeplayer.data.manga

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
}
