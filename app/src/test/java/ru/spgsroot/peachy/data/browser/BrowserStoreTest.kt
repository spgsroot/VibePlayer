package ru.spgsroot.peachy.data.browser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.spgsroot.peachy.data.db.AllowlistDao
import ru.spgsroot.peachy.data.db.AllowlistEntity
import ru.spgsroot.peachy.data.db.BookmarkDao
import ru.spgsroot.peachy.data.db.BookmarkEntity
import ru.spgsroot.peachy.data.db.BrowserHistoryDao
import ru.spgsroot.peachy.data.db.BrowserHistoryEntity

class BrowserStoreTest {
    private class MemoryBookmarks : BookmarkDao {
        val rows = MutableStateFlow<List<BookmarkEntity>>(emptyList())
        override fun observeAll(): Flow<List<BookmarkEntity>> = rows.map { r -> r.sortedByDescending { it.addedAt } }
        override suspend fun getAll() = rows.value
        override suspend fun upsert(entity: BookmarkEntity) {
            rows.value = rows.value.filterNot { it.url == entity.url } + entity
        }
        override suspend fun upsertAll(entities: List<BookmarkEntity>) = entities.forEach { upsert(it) }
        override suspend fun delete(url: String) {
            rows.value = rows.value.filterNot { it.url == url }
        }
    }

    private class MemoryHistory : BrowserHistoryDao() {
        val rows = MutableStateFlow<List<BrowserHistoryEntity>>(emptyList())
        override fun observeAll(): Flow<List<BrowserHistoryEntity>> = rows.map { r -> r.sortedByDescending { it.visitedAt } }
        override suspend fun getAll() = rows.value
        override suspend fun upsert(entity: BrowserHistoryEntity) {
            rows.value = rows.value.filterNot { it.url == entity.url } + entity
        }
        override suspend fun upsertAll(entities: List<BrowserHistoryEntity>) = entities.forEach { upsert(it) }
        override suspend fun delete(url: String) {
            rows.value = rows.value.filterNot { it.url == url }
        }
        override suspend fun clear() {
            rows.value = emptyList()
        }
        override suspend fun trim(keep: Int) {
            rows.value = rows.value.sortedByDescending { it.visitedAt }.take(keep)
        }
    }

    private class MemoryAllowlist : AllowlistDao {
        val hosts = MutableStateFlow<List<String>>(emptyList())
        override fun observeHosts(): Flow<List<String>> = hosts
        override suspend fun getHosts(): List<String> = hosts.value
        override suspend fun add(entity: AllowlistEntity) {
            if (entity.host !in hosts.value) hosts.value = hosts.value + entity.host
        }
        override suspend fun remove(host: String) {
            hosts.value = hosts.value - host
        }
    }

    private val bookmarks = MemoryBookmarks()
    private val history = MemoryHistory()
    private val allowlist = MemoryAllowlist()
    private val store = BrowserStore(bookmarks, history, allowlist)

    @Test
    fun bookmarkTogglesAndFallsBackToTheHostAsTitle() = runBlocking {
        assertTrue(store.toggleBookmark("https://www.example.com/a#top", "  ", saved = false, now = 1))
        assertEquals(listOf(BookmarkEntity("https://www.example.com/a", "example.com", 1)), store.bookmarks.first())
        assertFalse(store.toggleBookmark("https://www.example.com/a#other", "x", saved = true, now = 2))
        assertTrue(store.bookmarks.first().isEmpty())
    }

    @Test
    fun nonWebPagesAreNeverStored() = runBlocking {
        store.recordVisit("javascript:alert(1)", "x", now = 1)
        store.recordVisit("file:///sdcard/a.html", "x", now = 2)
        assertFalse(store.toggleBookmark("intent://x", "x", saved = false))
        assertTrue(store.history.first().isEmpty())
        assertTrue(store.bookmarks.first().isEmpty())
    }

    @Test
    fun revisitMovesThePageToTheTopWithoutDuplicates() = runBlocking {
        store.recordVisit("https://a.com/", "A", now = 1)
        store.recordVisit("https://b.com/", "B", now = 2)
        store.recordVisit("https://a.com/#frag", "A again", now = 3)
        assertEquals(listOf("A again", "B"), store.history.first().map { it.title })
    }

    @Test
    fun historyStaysBounded() = runBlocking {
        for (i in 1..BrowserHistoryDao.MAX_ROWS + 5) store.recordVisit("https://h$i.com/", "t", now = i.toLong())
        val rows = store.history.first()
        assertEquals(BrowserHistoryDao.MAX_ROWS, rows.size)
        assertEquals("https://h${BrowserHistoryDao.MAX_ROWS + 5}.com/", rows.first().url)
        assertNull(rows.firstOrNull { it.url == "https://h1.com/" })
    }

    @Test
    fun blockerSwitchCoversSubdomainsAndIgnoresWww() = runBlocking {
        store.setBlockerEnabled("www.Example.com", enabled = false)
        val allowed = store.allowedHosts.first()
        assertEquals(setOf("example.com"), allowed)
        assertTrue(BrowserStore.isBlockerOff(allowed, "cdn.example.com"))
        assertTrue(BrowserStore.isBlockerOff(allowed, "www.example.com"))
        assertFalse(BrowserStore.isBlockerOff(allowed, "notexample.com"))
        assertFalse(BrowserStore.isBlockerOff(allowed, null))
        store.setBlockerEnabled("example.com", enabled = true)
        assertTrue(store.allowedHosts.first().isEmpty())
    }
}
