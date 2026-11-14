package ru.spgsroot.peachy.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoMigrationTest {
    @Test fun migrationPreservesVideoRowsAndCreatesUsablePhotoColumns() {
        SQLiteDatabase.create(null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE videos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, filePath TEXT NOT NULL, duration INTEGER NOT NULL, thumbnailPath TEXT, " +
                "addedAt INTEGER NOT NULL, fileSize INTEGER NOT NULL, isCorrupted INTEGER NOT NULL)")
            sqlite.execSQL("INSERT INTO videos VALUES (7, 'existing', '/videos/7.mp4', 1234, NULL, 99, 123, 0)")
            sqlite.version = 11
            val database = Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java)
            ) { _, method, args ->
                check(method.name == "execSQL")
                sqlite.execSQL(args!![0] as String)
                null
            } as SupportSQLiteDatabase
            PhotoMigrations.MIGRATION_11_12.migrate(database)
            sqlite.version = 12
            sqlite.rawQuery("SELECT * FROM videos", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7L, cursor.getLong(0))
                assertEquals("existing", cursor.getString(1))
                assertEquals("/videos/7.mp4", cursor.getString(2))
                assertEquals(1234L, cursor.getLong(3))
                assertTrue(cursor.isNull(4))
                assertEquals(99L, cursor.getLong(5))
                assertEquals(123L, cursor.getLong(6))
                assertEquals(0, cursor.getInt(7))
                assertFalse(cursor.moveToNext())
            }
            val columns = mutableListOf<Triple<String, String, Int>>()
            sqlite.rawQuery("PRAGMA table_info(photos)", null).use { cursor ->
                while (cursor.moveToNext()) {
                    columns += Triple(cursor.getString(1), cursor.getString(2), cursor.getInt(3))
                }
            }
            assertEquals(listOf(
                Triple("id", "INTEGER", 1), Triple("title", "TEXT", 1), Triple("filePath", "TEXT", 1),
                Triple("thumbnailPath", "TEXT", 0), Triple("addedAt", "INTEGER", 1),
                Triple("fileSize", "INTEGER", 1), Triple("width", "INTEGER", 1), Triple("height", "INTEGER", 1)
            ), columns)
            sqlite.execSQL("INSERT INTO photos (title,filePath,thumbnailPath,addedAt,fileSize,width,height) " +
                "VALUES ('photo','/photos/new.jpg',NULL,100,456,640,480)")
            sqlite.rawQuery("SELECT * FROM photos", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getLong(0) > 0)
                assertEquals("photo", cursor.getString(1))
                assertEquals("/photos/new.jpg", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertEquals(100L, cursor.getLong(4))
                assertEquals(456L, cursor.getLong(5))
                assertEquals(640, cursor.getInt(6))
                assertEquals(480, cursor.getInt(7))
            }
        }
    }
}
