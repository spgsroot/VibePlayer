package ru.spgsroot.peachy.translate

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/** What the reader shows for one page. A page that is not in [TranslationRunner.pages] is idle. */
sealed interface PageState {
    object Idle : PageState
    object Loading : PageState
    data class Done(val translation: PageTranslation) : PageState
    data class Failed(val error: TranslationError) : PageState
}

/** Progress of translating a whole gallery. [stopped] is why the batch gave up, if it did. */
data class BatchState(val done: Int, val total: Int, val stopped: TranslationError? = null)

/**
 * Translates the pages of one gallery, one request per page: whoever asks first pays, whoever asks
 * while that request runs waits and then reads the cache. A batch reaches the same pages in order,
 * a few at a time, and gives up when the key is rejected or the provider asks it to slow down.
 */
class TranslationRunner(
    private val source: PageSource,
    private val translator: PageTranslator,
    private val cache: TranslationCache,
    private val model: () -> String,
    private val scope: CoroutineScope,
    private val parallelism: Int = 2
) {
    private val _pages = MutableStateFlow<Map<Int, PageState>>(emptyMap())
    val pages: StateFlow<Map<Int, PageState>> = _pages.asStateFlow()

    private val _batch = MutableStateFlow<BatchState?>(null)

    /** Null when no batch runs; after a batch gives up it keeps [BatchState.stopped] until [cancelBatch] or the next batch. */
    val batch: StateFlow<BatchState?> = _batch.asStateFlow()

    private val locks = ConcurrentHashMap<Pair<Int, String>, Mutex>()
    private var batchJob: Job? = null

    /** Shows a page the cache already holds, without the network. Returns false when there is none. */
    suspend fun showCached(galleryId: Int, page: Int, lang: String): Boolean {
        val cached = cache.get(galleryId, page, lang) ?: return false
        publish(page, PageState.Done(cached))
        return true
    }

    /**
     * Returns the final state of [page]. [lang] is an opaque [TranslationKey], kept separate from
     * the languages and engine in [request]. Failures become [PageState.Failed]; cancellation
     * propagates and leaves the page as it was.
     */
    suspend fun translate(
        galleryId: Int,
        page: Int,
        lang: String,
        request: TranslateRequest,
        force: Boolean = false
    ): PageState =
        locks.getOrPut(page to lang) { Mutex() }.withLock {
            if (!force) {
                cache.get(galleryId, page, lang)?.let { return@withLock publish(page, PageState.Done(it)) }
            }
            val before = _pages.value[page]
            publish(page, PageState.Loading)
            try {
                val jpeg = source.jpeg(galleryId, page)
                val translation = translator.translate(galleryId, jpeg, request)
                cache.put(galleryId, page, lang, model(), translation)
                publish(page, PageState.Done(translation))
            } catch (e: CancellationException) {
                restore(page, before)
                throw e
            } catch (e: TranslationException) {
                publish(page, PageState.Failed(e.error))
            } catch (e: IOException) {
                publish(page, PageState.Failed(TranslationError.Network))
            } catch (e: IllegalArgumentException) {
                publish(page, PageState.Failed(TranslationError.BadResponse))
            }
        }

    /** Starts translating pages `0 until pageCount` that are not cached yet. Does nothing while a batch runs. */
    fun startBatch(galleryId: Int, pageCount: Int, lang: String, request: TranslateRequest) {
        if (batchJob?.isActive == true) return
        batchJob = scope.launch {
            val self = coroutineContext.job
            val cached = cache.cachedPages(galleryId, lang).filter { it in 0 until pageCount }.toSet()
            _batch.value = BatchState(done = cached.size, total = pageCount)
            val gate = Semaphore(parallelism)
            coroutineScope {
                for (page in 0 until pageCount) {
                    if (page in cached) continue
                    gate.acquire()
                    launch {
                        try {
                            val state = translate(galleryId, page, lang, request)
                            val stop = (state as? PageState.Failed)?.error?.takeIf { it.endsBatch() }
                            _batch.update { it?.copy(done = it.done + 1, stopped = stop ?: it.stopped) }
                            if (stop != null) self.cancel()
                        } finally {
                            gate.release()
                        }
                    }
                }
            }
            _batch.value = null
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
        _batch.value = null
    }

    private fun TranslationError.endsBatch() =
        this == TranslationError.Auth || this == TranslationError.NotConfigured || this is TranslationError.RateLimit

    private fun publish(page: Int, state: PageState): PageState {
        _pages.update { it + (page to state) }
        return state
    }

    private fun restore(page: Int, before: PageState?) {
        _pages.update { if (before == null) it - page else it + (page to before) }
    }
}
