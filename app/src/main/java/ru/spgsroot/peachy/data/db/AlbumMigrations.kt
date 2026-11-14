package ru.spgsroot.peachy.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AlbumMigrations {
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE videos_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, filePath TEXT NOT NULL, duration INTEGER NOT NULL, thumbnailPath TEXT, addedAt INTEGER NOT NULL, fileSize INTEGER NOT NULL, isCorrupted INTEGER NOT NULL, albumId INTEGER, FOREIGN KEY(albumId) REFERENCES albums(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
            database.execSQL("INSERT INTO videos_new SELECT id,title,filePath,duration,thumbnailPath,addedAt,fileSize,isCorrupted,NULL FROM videos")
            database.execSQL("DROP TABLE videos")
            database.execSQL("ALTER TABLE videos_new RENAME TO videos")
            database.execSQL("CREATE INDEX index_videos_albumId ON videos(albumId)")
            database.execSQL("CREATE TABLE photos_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, filePath TEXT NOT NULL, thumbnailPath TEXT, addedAt INTEGER NOT NULL, fileSize INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, albumId INTEGER, FOREIGN KEY(albumId) REFERENCES albums(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
            database.execSQL("INSERT INTO photos_new SELECT id,title,filePath,thumbnailPath,addedAt,fileSize,width,height,NULL FROM photos")
            database.execSQL("DROP TABLE photos")
            database.execSQL("ALTER TABLE photos_new RENAME TO photos")
            database.execSQL("CREATE INDEX index_photos_albumId ON photos(albumId)")
        }
    }
}
