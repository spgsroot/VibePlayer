package ru.spgsroot.vibeplayer.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.vibeplayer.data.manga.NhentaiApi
import ru.spgsroot.vibeplayer.data.manga.TagPreferences
import ru.spgsroot.vibeplayer.manga.GalleryTag
import ru.spgsroot.vibeplayer.manga.TagFilter
import ru.spgsroot.vibeplayer.manga.TagLibrary
import ru.spgsroot.vibeplayer.manga.TagMode
import ru.spgsroot.vibeplayer.manga.TagRef
import ru.spgsroot.vibeplayer.manga.cycled
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

data class TagListState(
    val type: String = "tag",
    val query: String = "",
    val tags: List<GalleryTag> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false
)

@HiltViewModel
class TagBrowserViewModel @Inject constructor(
    private val api: NhentaiApi,
    private val preferences: TagPreferences
) : ViewModel() {
    private val _list = MutableStateFlow(TagListState())
    val list: StateFlow<TagListState> = _list

    private val _selection = MutableStateFlow<List<TagFilter>>(emptyList())
    val selection: StateFlow<List<TagFilter>> = _selection

    val library: StateFlow<TagLibrary> = preferences.library
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagLibrary())

    private var loadJob: Job? = null

    /** Loads the first page once; returning to an already loaded browser keeps its list and position. */
    fun start(type: String) {
        if (_list.value.page == 0 && !_list.value.loading) selectType(type)
    }

    fun selectType(type: String) {
        load(type, _list.value.query, debounce = false)
    }

    fun setQuery(query: String) {
        _list.update { it.copy(query = query) }
        load(_list.value.type, query, debounce = true)
    }

    fun retry() {
        val current = _list.value
        if (current.page == 0 || current.tags.isEmpty()) load(current.type, current.query, debounce = false)
        else loadMore()
    }

    fun loadMore() {
        val current = _list.value
        if (current.loading || !current.hasNext) return
        fetch(current.type, current.query, current.page + 1)
    }

    fun cycle(tag: TagRef) {
        _selection.update { it.cycled(tag.type, tag.name) }
    }

    fun remove(filter: TagFilter) {
        _selection.update { it - filter }
    }

    /** Adds every tag not already selected as an inclusion; tags that are already included or excluded stay as they are. */
    fun include(tags: List<TagRef>) {
        _selection.update { selected ->
            selected + tags
                .filter { tag -> selected.none { it.type == tag.type && it.name == tag.name } }
                .map { TagFilter(it.type, it.name, TagMode.Include) }
        }
    }

    fun clearSelection() {
        _selection.value = emptyList()
    }

    fun toggleFavorite(tag: TagRef) = edit { it.toggleFavorite(tag) }

    fun createCollection(name: String) = edit { it.createCollection(name) }

    fun renameCollection(name: String, newName: String) = edit { it.renameCollection(name, newName) }

    fun deleteCollection(name: String) = edit { it.deleteCollection(name) }

    fun toggleInCollection(name: String, tag: TagRef) = edit { it.toggleInCollection(name, tag) }

    private fun edit(change: (TagLibrary) -> TagLibrary) {
        viewModelScope.launch { preferences.update(change) }
    }

    private fun load(type: String, query: String, debounce: Boolean) {
        loadJob?.cancel()
        _list.update { TagListState(type = type, query = query, loading = true) }
        loadJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            request(type, query, page = 1)
        }
    }

    private fun fetch(type: String, query: String, page: Int) {
        loadJob?.cancel()
        _list.update { it.copy(loading = true, failed = false) }
        loadJob = viewModelScope.launch { request(type, query, page) }
    }

    private suspend fun request(type: String, query: String, page: Int) {
        try {
            val result = withContext(Dispatchers.IO) { api.tags(type, query, page) }
            _list.update {
                it.copy(
                    tags = if (page == 1) result.tags else it.tags + result.tags.filterNot { tag -> tag in it.tags },
                    page = page,
                    hasNext = result.hasNext,
                    loading = false,
                    failed = false
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _list.update { it.copy(loading = false, failed = true) }
        }
    }
}
