package ru.spgsroot.peachy.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.manga.GalleryMemoryStore
import ru.spgsroot.peachy.data.manga.MangaCatalogStore
import ru.spgsroot.peachy.data.manga.MangaLibraryRepository
import ru.spgsroot.peachy.data.manga.NhentaiApi
import ru.spgsroot.peachy.data.manga.TagPreferences
import ru.spgsroot.peachy.manga.CatalogQuery
import ru.spgsroot.peachy.manga.GallerySummary
import ru.spgsroot.peachy.manga.Recommendation
import ru.spgsroot.peachy.manga.Recommender
import ru.spgsroot.peachy.manga.SortKey
import ru.spgsroot.peachy.manga.TagFilter
import ru.spgsroot.peachy.manga.TagMode
import ru.spgsroot.peachy.manga.TagRef
import ru.spgsroot.peachy.manga.filterSearchResults
import ru.spgsroot.peachy.manga.toApi
import javax.inject.Inject

data class ForYouState(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val loaded: Boolean = false,
    /** False until the user has read, saved or favourited something; the list is then just what is popular. */
    val hasTaste: Boolean = true,
    val items: List<Recommendation> = emptyList()
)

/**
 * The "For you" tab. Everything is computed on the device from what the user opened, read, saved,
 * favourited, dismissed and tagged; the only network use is searching and reading "related" lists.
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val api: NhentaiApi,
    private val memory: GalleryMemoryStore,
    private val catalogStore: MangaCatalogStore,
    private val library: MangaLibraryRepository,
    private val tagPreferences: TagPreferences
) : ViewModel() {
    private val _state = MutableStateFlow(ForYouState())
    val state: StateFlow<ForYouState> = _state
    private var job: Job? = null

    /** Builds the list once; [refresh] builds it again. */
    fun load() {
        if (_state.value.loaded || _state.value.loading) return
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true, failed = false) }
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { build() }
                _state.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** Takes the gallery off the list at once and teaches the profile to steer away from its tags. */
    fun notInterested(gallery: GallerySummary) {
        _state.update { current -> current.copy(items = current.items.filterNot { it.gallery.id == gallery.id }) }
        viewModelScope.launch(Dispatchers.IO) { memory.dismiss(gallery) }
    }

    private suspend fun build(): ForYouState {
        val now = System.currentTimeMillis()
        val remembered = memory.all.first()
        val history = catalogStore.history.first().associateBy { it.galleryId }
        val saved = library.observe().first().associateBy { it.id }
        val tags = tagPreferences.library.first()

        val signals = remembered.map { gallery ->
            val read = history[gallery.id]
            val fraction = if (read != null && read.pageCount > 0) (read.page + 1).toDouble() / read.pageCount else 0.0
            Recommender.signalFor(
                gallery = gallery,
                readFraction = fraction,
                readAt = read?.readAt,
                saved = saved.containsKey(gallery.id),
                favorite = saved[gallery.id]?.favorite == true
            )
        }
        val explicit = HashMap<TagRef, Double>()
        tags.favorites.forEach { explicit.merge(it.lowered(), Recommender.FAVORITE_TAG, Double::plus) }
        tags.collections.flatMap { it.tags }.forEach { explicit.merge(it.lowered(), Recommender.COLLECTION_TAG, Double::plus) }

        val profile = Recommender.profile(signals, explicit, now)
        val seeds = Recommender.seeds(profile, signals, now)

        val candidates = ArrayList<GallerySummary>()
        var requests = 0
        var failures = 0
        fun attempt(load: () -> List<GallerySummary>) {
            requests++
            try {
                candidates += load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
            }
        }
        if (seeds.queries.isEmpty()) {
            attempt { api.search("", SortKey.Popular.toApi(), 1) }
        }
        for (query in seeds.queries) {
            val text = CatalogQuery(
                tags = query.map { TagFilter(it.type, it.name, TagMode.Include) },
                sort = SortKey.Popular
            ).toSearchQuery()
            attempt { api.search(text, SortKey.Popular.toApi(), 1) }
        }
        for (id in seeds.relatedIds) attempt { api.related(id) }
        if (requests > 0 && failures == requests) return ForYouState(loaded = true, failed = true, hasTaste = profile.hasTaste)

        val excluded = HashSet<Int>().apply {
            addAll(history.keys)
            addAll(saved.keys)
            remembered.filter { it.dismissed }.mapTo(this) { it.id }
        }
        val visible = filterSearchResults(candidates, emptyMap(), catalogStore::blocks)
        val items = Recommender.rank(visible, profile, excluded, RESULT_LIMIT)
        return ForYouState(loaded = true, hasTaste = profile.hasTaste, items = items)
    }

    private fun TagRef.lowered() = copy(name = name.lowercase())

    private companion object {
        const val RESULT_LIMIT = 30
    }
}
