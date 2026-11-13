package ru.spgsroot.peachy.data.browser

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object BrowserMigrations {
    /** Adds the browser's bookmarks, history and per-site blocker switches; nothing else is touched. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS bookmarks (url TEXT NOT NULL PRIMARY KEY, " +
                    "title TEXT NOT NULL, addedAt INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS browser_history (url TEXT NOT NULL PRIMARY KEY, " +
                    "title TEXT NOT NULL, visitedAt INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS adblock_allowlist (host TEXT NOT NULL PRIMARY KEY)"
            )
        }
    }
}
