package ru.spgsroot.peachy.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumMigrationTest {
    @Test fun migrationRetainsExistingMediaAndAlbumDeletionOnlyClearsMembership() {
        SQLiteDatabase.create(null).use { sqlite ->
            sqlite.setForeignKeyConstraintsEnabled(true)
            sqlite.execSQL("CREATE TABLE videos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, filePath TEXT NOT NULL, duration INTEGER NOT NULL, thumbnailPath TEXT, addedAt INTEGER NOT NULL, fileSize INTEGER NOT NULL, isCorrupted INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, filePath TEXT NOT NULL, thumbnailPath TEXT, addedAt INTEGER NOT NULL, fileSize INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL)")
            sqlite.execSQL("INSERT INTO videos VALUES(7,'video','/v',123,NULL,99,12,0)")
            sqlite.execSQL("INSERT INTO photos VALUES(7,'photo','/p','/thumb',100,8,640,480)")
            val proxy = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
                check(method.name == "execSQL"); sqlite.execSQL(args!![0] as String); null
            } as SupportSQLiteDatabase
            AlbumMigrations.MIGRATION_12_13.migrate(proxy)
            for (table in listOf("videos", "photos")) sqlite.rawQuery("SELECT id,title,filePath,albumId FROM $table", null).use {
                assertTrue(it.moveToFirst()); assertEquals(7L, it.getLong(0)); assertEquals(if (table == "videos") "video" else "photo", it.getString(1))
                assertEquals(if (table == "videos") "/v" else "/p", it.getString(2)); assertTrue(it.isNull(3)); assertFalse(it.moveToNext())
            }
            sqlite.execSQL("INSERT INTO albums VALUES(3,'Mixed',1)")
            sqlite.execSQL("UPDATE videos SET albumId=3")
            sqlite.execSQL("UPDATE photos SET albumId=3")
            sqlite.execSQL("DELETE FROM albums WHERE id=3")
            sqlite.rawQuery("SELECT duration,fileSize,albumId FROM videos WHERE id=7", null).use {
                assertTrue(it.moveToFirst()); assertEquals(123L,it.getLong(0)); assertEquals(12L,it.getLong(1)); assertTrue(it.isNull(2))
            }
            sqlite.rawQuery("SELECT width,height,thumbnailPath,albumId FROM photos WHERE id=7", null).use {
                assertTrue(it.moveToFirst()); assertEquals(640,it.getInt(0)); assertEquals(480,it.getInt(1)); assertEquals("/thumb",it.getString(2)); assertTrue(it.isNull(3))
            }
            sqlite.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
        }
    }
}
