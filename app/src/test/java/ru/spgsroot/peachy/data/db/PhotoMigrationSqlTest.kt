package ru.spgsroot.peachy.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoMigrationSqlTest {
    @Test fun migration11To12OnlyCreatesSeparatePhotoTable() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            check(method.name == "execSQL") { "Unexpected database call: ${method.name}" }
            statements += args!![0] as String
            null
        } as SupportSQLiteDatabase
        val migration = PhotoMigrations.MIGRATION_11_12
        assertEquals(11, migration.startVersion)
        assertEquals(12, migration.endVersion)
        migration.migrate(database)
        assertEquals(
            listOf("CREATE TABLE IF NOT EXISTS photos (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, filePath TEXT NOT NULL, thumbnailPath TEXT, addedAt INTEGER NOT NULL, " +
                "fileSize INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL)"),
            statements
        )
    }
}
