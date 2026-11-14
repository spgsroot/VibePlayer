package ru.spgsroot.peachy.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PhotoMigrations {
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "title TEXT NOT NULL, filePath TEXT NOT NULL, thumbnailPath TEXT, addedAt INTEGER NOT NULL, " +
                    "fileSize INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL)"
            )
        }
    }
}
