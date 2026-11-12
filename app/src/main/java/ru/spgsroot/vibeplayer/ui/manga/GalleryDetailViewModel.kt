package ru.spgsroot.vibeplayer.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.vibeplayer.data.manga.MangaCatalogStore
import ru.spgsroot.vibeplayer.data.manga.MangaLibraryRepository
import ru.spgsroot.vibeplayer.data.manga.NhentaiApi
import ru.spgsroot.vibeplayer.manga.GalleryComment
import ru.spgsroot.vibeplayer.manga.GalleryDetail
import ru.spgsroot.vibeplayer.manga.GallerySummary
import javax.inject.Inject

/** A list loaded from the API on its own, so one failing section never hides the rest of the page. */
data class Section<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val loaded: Boolean = false
)

data class DetailState(
    val detail: GalleryDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val savedPage: Int? = null,
    val related: Section<GallerySummary> = Section(),
    val comments: Section<GalleryComment> = Section()
)

@HiltViewModel
class GalleryDetailViewModel @Inject constructor(
    private val api: NhentaiApi,
    private val library: MangaLibraryRepository,
    private val catalogStore: MangaCatalogStore
) : ViewModel() {
    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    val libraryItems = library.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadedId: Int? = null

    fun load(id: Int) {
        if (loadedId == id) return
        loadedId = id
        _state.value = DetailState()
        viewModelScope.launch {
            val savedPage = withContext(Dispatchers.IO) { catalogStore.getHistory(id)?.page }
            val saved = library.observe().first().find { it.id == id && it.pagePaths.isNotEmpty() }
            _state.update { it.copy(savedPage = savedPage) }
            if (saved != null) {
                _state.update {
                    it.copy(detail = GalleryDetail(saved.id, saved.title, saved.thumbnailPath, saved.pagePaths), loading = false)
                }
            }
            try {
                val fetched = withContext(Dispatchers.IO) { api.gallery(id) }
                catalogStore.rememberTags(fetched.tags)
                _state.update { it.copy(detail = fetched, loading = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A saved copy stays readable offline; only a gallery we could not show at all is an error.
                _state.update { if (it.detail == null) it.copy(loading = false, error = e.message ?: "Open failed") else it }
            }
            loadRelated(id)
        }
    }

    fun retry() {
        val id = loadedId ?: return
        loadedId = null
        load(id)
    }

    fun loadRelated(id: Int) {
        _state.update { it.copy(related = it.related.copy(loading = true, failed = false)) }
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { api.related(id) }
                _state.update { it.copy(related = Section(items, loaded = true)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(related = Section(failed = true)) }
            }
        }
    }

    /** Comments are read on request: the endpoint is rate limited and most galleries are opened only to read. */
    fun loadComments() {
        val id = loadedId ?: return
        if (_state.value.comments.loading) return
        _state.update { it.copy(comments = it.comments.copy(loading = true, failed = false)) }
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { api.comments(id) }
                _state.update { it.copy(comments = Section(items, loaded = true)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(comments = Section(failed = true)) }
            }
        }
    }

    fun isSaved(id: Int): Boolean = libraryItems.value.any { it.id == id }

    fun isFavorite(id: Int): Boolean = libraryItems.value.any { it.id == id && it.favorite }

    fun download() {
        val current = _state.value.detail ?: return
        if (_progress.value != null) return
        _actionError.value = null
        viewModelScope.launch {
            _progress.value = "0/${current.pagePaths.size}"
            try {
                library.save(current, isFavorite(current.id)) { done, total -> _progress.value = "$done/$total" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Save failed"
            } finally {
                _progress.value = null
            }
        }
    }

    fun removeDownload() {
        val current = _state.value.detail ?: return
        viewModelScope.launch { library.remove(current.id) }
    }

    fun toggleFavorite() {
        val current = _state.value.detail ?: return
        viewModelScope.launch { library.setFavorite(current, !isFavorite(current.id)) }
    }
}
