package ru.spgsroot.peachy.data.manga

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import ru.spgsroot.peachy.data.db.ReadHistoryDao
import ru.spgsroot.peachy.data.db.ReadHistoryEntity
import ru.spgsroot.peachy.data.db.SearchHistoryDao
import ru.spgsroot.peachy.data.db.SearchHistoryEntity
import ru.spgsroot.peachy.data.db.TagBlacklistDao
import ru.spgsroot.peachy.data.db.TagBlacklistEntity
import ru.spgsroot.peachy.manga.GalleryTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaMigrationSqlTest {
    @Test
    fun rememberedQueriesStayDistinctNewestFirstAndBoundedAfterReload() = runBlocking {
        val searches = MemorySearchHistoryDao()
        val store = MangaCatalogStore(MemoryReadHistoryDao(), searches, MemoryTagBlacklistDao())
        for (index in 1..11) store.rememberQuery("query$index")
        store.rememberQuery(" query5 ")
        store.rememberQuery(" \t ")

        val reloaded = MangaCatalogStore(MemoryReadHistoryDao(), searches, MemoryTagBlacklistDao())
        assertEquals(
            listOf("query5", "query11", "query10", "query9", "query8", "query7", "query6", "query4", "query3", "query2"),
            reloaded.queries.first()
        )
        assertEquals(10, searches.getAll().size)
    }

    @Test
    fun sameTimestampQueriesKeepRecencyAndBlanksDoNotRewriteHistory() = runBlocking {
        val searches = MemorySearchHistoryDao()
        searches.remember("first", 100)
        searches.remember("second", 100)
        searches.remember("third", 99)
        assertEquals(listOf("third", "second", "first"), searches.getAll().map { it.query })
        val before = searches.getAll()
        searches.remember(" \n ", 200)
        assertEquals(before, searches.getAll())
    }

    @Test
    fun progressIsUpsertedAndAvailableAfterRecreatingStore() = runBlocking {
        val history = MemoryReadHistoryDao()
        val searches = MemorySearchHistoryDao()
        val blacklist = MemoryTagBlacklistDao()
        val store = MangaCatalogStore(history, searches, blacklist)
        store.upsertHistory(ReadHistoryEntity(1, "First", "one.jpg", 2, 20, 100))
        store.upsertHistory(ReadHistoryEntity(2, "Second", "two.jpg", 4, 30, 200))
        store.upsertHistory(ReadHistoryEntity(1, "Updated", "new.jpg", 8, 20, 300))

        val reloaded = MangaCatalogStore(history, searches, blacklist)
        assertEquals(listOf(1, 2), reloaded.history.first().map { it.galleryId })
        assertEquals(ReadHistoryEntity(1, "Updated", "new.jpg", 8, 20, 300), reloaded.getHistory(1))
        assertEquals(null, reloaded.getHistory(999))
    }

    @Test
    fun blacklistPersistsIdsAndLowercaseExactNamesAndSupportsRemoval() = runBlocking {
        val blacklist = MemoryTagBlacklistDao()
        val history = MemoryReadHistoryDao()
        val searches = MemorySearchHistoryDao()
        val store = MangaCatalogStore(history, searches, blacklist)
        store.blockId(7)
        store.blockName("School Uniform")
        store.blockName("SCHOOL UNIFORM")

        val reloaded = MangaCatalogStore(history, searches, blacklist)
        assertEquals(setOf(7), reloaded.blockedIds())
        assertEquals(setOf("school uniform"), reloaded.blockedNames())
        assertEquals(2, blacklist.getAll().size)
        assertTrue(reloaded.blocks(listOf(7), emptyList()))
        assertTrue(reloaded.blocks(emptyList(), listOf("SCHOOL UNIFORM")))
        assertFalse(reloaded.blocks(listOf(8), listOf("school", "school uniform extra")))
        reloaded.unblockId(7)
        reloaded.unblockName("SCHOOL UNIFORM")
        assertTrue(reloaded.blockedIds().isEmpty())
        assertTrue(reloaded.blockedNames().isEmpty())
    }

    @Test
    fun learnedTagNamesBlockLaterChecksOnTheSameStore() = runBlocking {
        val store = MangaCatalogStore(MemoryReadHistoryDao(), MemorySearchHistoryDao(), MemoryTagBlacklistDao())
        store.blockName("english")
        assertFalse(store.blocks(listOf(7), emptyList()))
        store.rememberTags(listOf(GalleryTag(7, "language", "English")))
        assertTrue(store.blocks(listOf(7), emptyList()))
        assertFalse(store.blocks(listOf(8), emptyList()))
        store.unblockName("english")
        assertFalse(store.blocks(listOf(7), emptyList()))
    }

    @Test
    fun migrationCreatesCatalogTablesWithoutTouchingSavedLibrary() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            check(method.name == "execSQL") { "Unexpected database call: ${method.name}" }
            statements += args!![0] as String
            null
        } as SupportSQLiteDatabase

        val migration = MangaMigrations.MIGRATION_5_6
        assertEquals(5, migration.startVersion)
        assertEquals(6, migration.endVersion)
        migration.migrate(database)

        assertEquals(3, statements.size)
        assertTrue(statements.any { it.startsWith("CREATE TABLE IF NOT EXISTS read_history") })
        assertTrue(statements.any { it.startsWith("CREATE TABLE IF NOT EXISTS search_history") })
        assertTrue(statements.any { it.startsWith("CREATE TABLE IF NOT EXISTS tag_blacklist") })
        assertFalse(statements.any { it.contains("library_galleries", ignoreCase = true) })
        assertTrue(statements.all { it.startsWith("CREATE TABLE IF NOT EXISTS ") })
        assertEquals(
            "CREATE TABLE IF NOT EXISTS read_history (galleryId INTEGER NOT NULL PRIMARY KEY, " +
                "title TEXT NOT NULL, thumbnailPath TEXT NOT NULL, page INTEGER NOT NULL, " +
                "pageCount INTEGER NOT NULL, readAt INTEGER NOT NULL)",
            statements[0]
        )
        assertEquals(
            "CREATE TABLE IF NOT EXISTS search_history (query TEXT NOT NULL PRIMARY KEY, usedAt INTEGER NOT NULL)",
            statements[1]
        )
        assertEquals(
            "CREATE TABLE IF NOT EXISTS tag_blacklist (kind TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(kind, value))",
            statements[2]
        )
    }
}

private class MemorySearchHistoryDao : SearchHistoryDao() {
    private val rows = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())
    override fun observeAll(): Flow<List<SearchHistoryEntity>> = rows.map { it.sortedByDescending { row -> row.usedAt } }
    override suspend fun getAll(): List<SearchHistoryEntity> = rows.value.sortedByDescending { it.usedAt }
    override suspend fun insertAll(entities: List<SearchHistoryEntity>) {
        rows.value = entities
    }
    override suspend fun clear() {
        rows.value = emptyList()
    }
}

private class MemoryReadHistoryDao : ReadHistoryDao {
    private val rows = MutableStateFlow<List<ReadHistoryEntity>>(emptyList())
    override fun observeAll(): Flow<List<ReadHistoryEntity>> = rows.map { it.sortedByDescending { row -> row.readAt } }
    override suspend fun getById(galleryId: Int): ReadHistoryEntity? = rows.value.find { it.galleryId == galleryId }
    override suspend fun upsert(entity: ReadHistoryEntity) {
        rows.value = rows.value.filterNot { it.galleryId == entity.galleryId } + entity
    }
}

private class MemoryTagBlacklistDao : TagBlacklistDao {
    private val rows = mutableListOf<TagBlacklistEntity>()
    override suspend fun getAll(): List<TagBlacklistEntity> = rows.toList()
    override suspend fun upsert(entity: TagBlacklistEntity) {
        rows.removeAll { it.kind == entity.kind && it.value == entity.value }
        rows += entity
    }
    override suspend fun delete(kind: String, value: String) {
        rows.removeAll { it.kind == kind && it.value == value }
    }
}
