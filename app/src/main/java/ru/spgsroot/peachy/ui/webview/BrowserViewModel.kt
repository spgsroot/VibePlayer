package ru.spgsroot.peachy.ui.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.browser.FoundVideo
import ru.spgsroot.peachy.browser.MediaKind
import ru.spgsroot.peachy.browser.MediaSniffer
import ru.spgsroot.peachy.browser.adblock.FilterEngine
import ru.spgsroot.peachy.data.browser.AdblockService
import ru.spgsroot.peachy.data.browser.BrowserStore
import ru.spgsroot.peachy.data.db.BookmarkEntity
import ru.spgsroot.peachy.data.db.BrowserHistoryEntity
import ru.spgsroot.peachy.data.notes.NoteKind
import ru.spgsroot.peachy.data.notes.NoteRepository
import ru.spgsroot.peachy.data.storage.VideoImporter

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val store: BrowserStore,
    private val adblock: AdblockService,
    private val importer: VideoImporter,
    private val notes: NoteRepository
) : ViewModel() {

    val bookmarks: StateFlow<List<BookmarkEntity>> = store.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<BrowserHistoryEntity>> = store.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Eagerly collected: the request thread reads [allowedNow] without suspending. */
    val allowedHosts: StateFlow<Set<String>> = store.allowedHosts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val engine: StateFlow<FilterEngine> = adblock.engine

    init {
        viewModelScope.launch { adblock.ensureLoaded() }
    }

    fun allowedNow(): Set<String> = allowedHosts.value

    /** Text of each page note, by the same address key the bookmarks use. */
    val pageNotes: StateFlow<Map<String, String>> = notes.observeAll()
        .map { rows -> rows.filter { it.kind == NoteKind.PAGE }.associate { it.ref to it.text } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun savePageNote(pageKey: String, text: String) {
        viewModelScope.launch { notes.save(NoteKind.PAGE, pageKey, text) }
    }

    fun toggleBookmark(url: String, title: String, saved: Boolean) {
        viewModelScope.launch { store.toggleBookmark(url, title, saved) }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { store.removeBookmark(url) }
    }

    fun recordVisit(url: String, title: String) {
        viewModelScope.launch { store.recordVisit(url, title) }
    }

    fun removeVisit(url: String) {
        viewModelScope.launch { store.removeVisit(url) }
    }

    fun clearHistory() {
        viewModelScope.launch { store.clearHistory() }
    }

    /** [onApplied] runs once the new setting is visible to request filtering, so a reload can use it. */
    fun setBlockerEnabled(host: String, enabled: Boolean, onApplied: () -> Unit) {
        viewModelScope.launch {
            store.setBlockerEnabled(host, enabled)
            allowedHosts.first { BrowserStore.isBlockerOff(it, host) == !enabled }
            onApplied()
        }
    }

    /** Fetches newer filter lists; [onDone] gets the number of rules on success. */
    fun updateFilters(onDone: (Result<Int>) -> Unit) {
        viewModelScope.launch { onDone(adblock.update()) }
    }

    /**
     * Saves a video the page offered into the library. Only HTTP(S) addresses that do not point
     * into the user's own network are fetched, and streams (HLS, DASH) are refused.
     */
    fun download(video: FoundVideo, pageHost: String?, headers: Map<String, String>, title: String?, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = when {
                video.kind == MediaKind.STREAM -> Result.failure(IllegalArgumentException("stream"))
                !MediaSniffer.isAllowedDownload(video.url, pageHost) -> Result.failure(IllegalArgumentException("blocked"))
                else -> importer.importUrl(video.url, headers, title)
            }
            onDone(result)
        }
    }
}
