package ru.spgsroot.peachy.data.notes

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NoteMigrations {
    /** Adds the notes table; nothing else is touched. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS notes (kind TEXT NOT NULL, ref TEXT NOT NULL, " +
                    "text TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(kind, ref))"
            )
        }
    }
}
