package ru.spgsroot.vibeplayer.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.vibeplayer.data.manga.MangaLibraryRepository
import ru.spgsroot.vibeplayer.data.manga.MangaCatalogStore
import ru.spgsroot.vibeplayer.data.manga.NhentaiApi
import ru.spgsroot.vibeplayer.manga.CatalogQuery
import ru.spgsroot.vibeplayer.manga.SearchDestination
import ru.spgsroot.vibeplayer.manga.searchDestination
import ru.spgsroot.vibeplayer.manga.toApi
import ru.spgsroot.vibeplayer.device.buttplug.CommandSender
import ru.spgsroot.vibeplayer.manga.GalleryDetail
import ru.spgsroot.vibeplayer.manga.GallerySummary
import ru.spgsroot.vibeplayer.manga.LibraryGallery
import ru.spgsroot.vibeplayer.manga.NhentaiCdn
import ru.spgsroot.vibeplayer.manga.PageTurnHaptic
import java.io.File
import javax.inject.Inject

data class CatalogState(
    val query: CatalogQuery = CatalogQuery(),
    val page: Int = 1,
    val results: List<GallerySummary> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val searched: Boolean = false,
    val hasNext: Boolean = false
)

@HiltViewModel
class MangaViewModel @Inject constructor(
    private val api: NhentaiApi,
    private val library: MangaLibraryRepository,
    private val commandSender: CommandSender,
    private val catalogStore: MangaCatalogStore
) : ViewModel() {
    val libraryItems: StateFlow<List<LibraryGallery>> = library.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history = catalogStore.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val queries = catalogStore.queries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _catalog = MutableStateFlow(CatalogState())
    val catalog: StateFlow<CatalogState> = _catalog
    private var searchJob: Job? = null
    private var rawResults: List<GallerySummary> = emptyList()
    private val _blockedIds = MutableStateFlow<Set<Int>>(emptySet())
    val blockedIds: StateFlow<Set<Int>> = _blockedIds
    private val _blockedNames = MutableStateFlow<Set<String>>(emptySet())
    val blockedNames: StateFlow<Set<String>> = _blockedNames

    init {
        viewModelScope.launch { refreshBlacklist() }
    }

    private val _detail = MutableStateFlow<GalleryDetail?>(null)
    val detail: StateFlow<GalleryDetail?> = _detail

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress

    private val _intensity = MutableStateFlow(PageTurnHaptic.DEFAULT_INTENSITY)
    val intensity: StateFlow<Float> = _intensity

    fun search(query: CatalogQuery, page: Int = 1) {
        if (searchDestination(query.text) is SearchDestination.OpenGallery) return
        searchJob?.cancel()
        rawResults = emptyList()
        _catalog.value = CatalogState(query = query, page = page, loading = true, searched = true)
        searchJob = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    catalogStore.rememberQuery(query.toSearchQuery())
                    api.search(query.toSearchQuery(), query.sort.toApi(), page)
                }
                rawResults = results
                refreshVisibleResults()
                // The API exposes no total/page count. An empty raw page ends paging;
                // a fully blacklisted page must still allow moving forward.
                _catalog.value = _catalog.value.copy(loading = false, hasNext = results.isNotEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _catalog.value = _catalog.value.copy(loading = false, failed = true)
            }
        }
    }

    fun blockId(id: Int) = changeBlacklist { catalogStore.blockId(id) }
    fun unblockId(id: Int) = changeBlacklist { catalogStore.unblockId(id) }
    fun blockName(name: String) = changeBlacklist { catalogStore.blockName(name.trim()) }
    fun unblockName(name: String) = changeBlacklist { catalogStore.unblockName(name) }

    private fun changeBlacklist(change: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { change() }
            refreshBlacklist()
            refreshVisibleResults()
        }
    }

    private suspend fun refreshBlacklist() {
        _blockedIds.value = withContext(Dispatchers.IO) { catalogStore.blockedIds() }
        _blockedNames.value = withContext(Dispatchers.IO) { catalogStore.blockedNames() }
    }

    private suspend fun refreshVisibleResults() {
        val source = rawResults
        val visible = withContext(Dispatchers.IO) {
            source.filterNot { catalogStore.blocks(it.tagIds, emptyList()) }
        }
        if (rawResults === source) _catalog.value = _catalog.value.copy(results = visible)
    }

    fun open(id: Int) {
        val saved = libraryItems.value.find { it.id == id }
        if (saved != null && saved.pagePaths.isNotEmpty()) {
            _detail.value = GalleryDetail(saved.id, saved.title, saved.thumbnailPath, saved.pagePaths)
        }
        viewModelScope.launch {
            _error.value = null
            try {
                _detail.value = withContext(Dispatchers.IO) { api.gallery(id) }
            } catch (e: Exception) {
                if (_detail.value == null) _error.value = e.message ?: "Open failed"
            }
        }
    }

    fun pageModel(path: String): Any? {
        val id = _detail.value?.id ?: return null
        val local = library.localPage(id, path)
        if (local != null && local.exists()) return local
        return NhentaiCdn.image(path)
    }

    fun isSaved(id: Int): Boolean = libraryItems.value.any { it.id == id }

    fun isFavorite(id: Int): Boolean = libraryItems.value.any { it.id == id && it.favorite }

    fun saveCurrent() {
        val current = _detail.value ?: return
        viewModelScope.launch {
            _error.value = null
            try {
                library.save(current, isFavorite(current.id)) { done, total ->
                    _progress.value = "$done/$total"
                }
                _progress.value = null
            } catch (e: Exception) {
                _progress.value = null
                _error.value = e.message ?: "Save failed"
            }
        }
    }

    fun toggleFavorite() {
        val current = _detail.value ?: return
        viewModelScope.launch {
            val next = !isFavorite(current.id)
            library.setFavorite(current, next)
        }
    }

    fun remove(id: Int) {
        viewModelScope.launch { library.remove(id) }
    }

    fun setIntensity(value: Float) {
        _intensity.value = value
    }

    fun onPageTurned() {
        commandSender.pulse(_intensity.value, PageTurnHaptic.DEFAULT_DURATION_MS)
    }

    fun localFile(path: String): File? {
        val id = _detail.value?.id ?: return null
        return library.localPage(id, path)?.takeIf { it.exists() }
    }
}
