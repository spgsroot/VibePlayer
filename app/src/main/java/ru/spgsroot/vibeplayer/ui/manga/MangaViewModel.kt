package ru.spgsroot.vibeplayer.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.vibeplayer.data.manga.MangaLibraryRepository
import ru.spgsroot.vibeplayer.data.manga.NhentaiApi
import ru.spgsroot.vibeplayer.data.manga.NhentaiException
import ru.spgsroot.vibeplayer.device.buttplug.CommandSender
import ru.spgsroot.vibeplayer.manga.GalleryDetail
import ru.spgsroot.vibeplayer.manga.GallerySummary
import ru.spgsroot.vibeplayer.manga.LibraryGallery
import ru.spgsroot.vibeplayer.manga.NhentaiCdn
import ru.spgsroot.vibeplayer.manga.PageTurnHaptic
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MangaViewModel @Inject constructor(
    private val api: NhentaiApi,
    private val library: MangaLibraryRepository,
    private val commandSender: CommandSender
) : ViewModel() {
    val libraryItems: StateFlow<List<LibraryGallery>> = library.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _search = MutableStateFlow<List<GallerySummary>>(emptyList())
    val search: StateFlow<List<GallerySummary>> = _search

    private val _detail = MutableStateFlow<GalleryDetail?>(null)
    val detail: StateFlow<GalleryDetail?> = _detail

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress

    private val _intensity = MutableStateFlow(PageTurnHaptic.DEFAULT_INTENSITY)
    val intensity: StateFlow<Float> = _intensity

    fun search(query: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                _search.value = withContext(Dispatchers.IO) { api.search(query) }
            } catch (e: NhentaiException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "Search failed"
            }
        }
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
