package ru.spgsroot.peachy.data.browser

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.spgsroot.peachy.browser.BrowserUrls
import ru.spgsroot.peachy.browser.adblock.Hosts
import ru.spgsroot.peachy.data.db.AllowlistDao
import ru.spgsroot.peachy.data.db.AllowlistEntity
import ru.spgsroot.peachy.data.db.BookmarkDao
import ru.spgsroot.peachy.data.db.BookmarkEntity
import ru.spgsroot.peachy.data.db.BrowserHistoryDao
import ru.spgsroot.peachy.data.db.BrowserHistoryEntity

/** Bookmarks, visited pages and the sites where the blocker is switched off. */
@Singleton
class BrowserStore @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: BrowserHistoryDao,
    private val allowlistDao: AllowlistDao
) {
    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()
    val history: Flow<List<BrowserHistoryEntity>> = historyDao.observeAll()
    val allowedHosts: Flow<Set<String>> = allowlistDao.observeHosts().map { it.toSet() }

    /** Adds the page, or removes it when it is already saved. Returns true when it is saved afterwards. */
    suspend fun toggleBookmark(url: String, title: String, saved: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val key = pageKey(url) ?: return saved
        return if (saved) {
            bookmarkDao.delete(key)
            false
        } else {
            bookmarkDao.upsert(BookmarkEntity(key, title.trim().ifEmpty { BrowserUrls.displayHost(key) }, now))
            true
        }
    }

    suspend fun removeBookmark(url: String) = bookmarkDao.delete(url)

    /** Records a visit to a web page; other schemes and blank pages are not kept. */
    suspend fun recordVisit(url: String, title: String, now: Long = System.currentTimeMillis()) {
        val key = pageKey(url) ?: return
        historyDao.record(BrowserHistoryEntity(key, title.trim().ifEmpty { BrowserUrls.displayHost(key) }, now))
    }

    suspend fun removeVisit(url: String) = historyDao.delete(url)

    suspend fun clearHistory() = historyDao.clear()

    suspend fun setBlockerEnabled(host: String, enabled: Boolean) {
        val key = siteKey(host) ?: return
        if (enabled) allowlistDao.remove(key) else allowlistDao.add(AllowlistEntity(key))
    }

    companion object {
        /** The address as stored: web pages only, without the fragment. */
        fun pageKey(url: String): String? {
            if (!BrowserUrls.isWebUrl(url)) return null
            return url.trim().substringBefore('#')
        }

        /** The name the blocker switch is stored under: the host without a leading `www.`. */
        fun siteKey(host: String?): String? = host?.trim()?.lowercase()?.removePrefix("www.")?.ifEmpty { null }

        /** True when the blocker is off for [host] because it, or a parent domain, is allowed. */
        fun isBlockerOff(allowed: Set<String>, host: String?): Boolean {
            val key = siteKey(host) ?: return false
            return allowed.any { Hosts.matchesDomain(key, it) }
        }
    }
}
