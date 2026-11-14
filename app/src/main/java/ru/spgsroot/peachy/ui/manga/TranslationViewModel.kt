package ru.spgsroot.peachy.ui.manga

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.locale.LocaleManager
import ru.spgsroot.peachy.manga.ReaderSettings
import ru.spgsroot.peachy.manga.TranslateMode
import ru.spgsroot.peachy.translate.BatchState
import ru.spgsroot.peachy.translate.PageBytes
import ru.spgsroot.peachy.translate.PageState
import ru.spgsroot.peachy.translate.PageTranslator
import ru.spgsroot.peachy.translate.ProviderSettings
import ru.spgsroot.peachy.translate.ProviderSettingsStore
import ru.spgsroot.peachy.translate.TargetLanguage
import ru.spgsroot.peachy.translate.TranslateRequest
import ru.spgsroot.peachy.translate.TranslationClient
import ru.spgsroot.peachy.translate.TranslationError
import ru.spgsroot.peachy.translate.TranslationException
import ru.spgsroot.peachy.translate.TranslationKey
import ru.spgsroot.peachy.translate.TranslationRunner
import ru.spgsroot.peachy.translate.TranslationStore

/**
 * Translation for the gallery the reader has open. Nothing is sent before the user has agreed to
 * which host receives the pages; a request that arrives earlier waits for the answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TranslationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerStore: ProviderSettingsStore,
    private val pageBytes: PageBytes,
    private val store: TranslationStore,
    private val client: TranslationClient,
    private val translator: PageTranslator
) : ViewModel() {
    private val runner = MutableStateFlow<TranslationRunner?>(null)
    private var galleryId = -1
    private var pageCount = 0
    private var activeKey: String? = null
    private var pendingAfterConsent: (() -> Unit)? = null

    // The user said no this session: a page turn must not ask again, only a deliberate press does.
    private var declined = false

    val pages: StateFlow<Map<Int, PageState>> = runner
        .flatMapLatest { it?.pages ?: flowOf(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val batch: StateFlow<BatchState?> = runner
        .flatMapLatest { it?.batch ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _needsConsent = MutableStateFlow(false)

    /** True when a request is wanted but the user has not yet agreed to send pages to the provider. */
    val needsConsent: StateFlow<Boolean> = _needsConsent.asStateFlow()

    val provider: StateFlow<ProviderSettings> = providerStore.settings

    /** Binds the view model to a gallery; opening the one that is already open changes nothing. */
    fun open(galleryId: Int, pagePaths: List<String>) {
        if (galleryId == this.galleryId && runner.value != null) return
        runner.value?.cancelBatch()
        pendingAfterConsent = null
        declined = false
        _needsConsent.value = false
        this.galleryId = galleryId
        pageCount = pagePaths.size
        activeKey = null
        pageBytes.bind(pagePaths)
        runner.value = newRunner()
    }

    /**
     * Translates [page]. A page the cache already holds is shown without the consent question: nothing
     * is sent. [explicit] is false for a request nobody pressed a button for (a page turn).
     */
    fun translateNow(page: Int, settings: ReaderSettings, force: Boolean = false, explicit: Boolean = true) {
        val request = request(settings)
        val key = TranslationKey.of(request.engine, request.source, request.target)
        val current = runnerFor(key) ?: return
        val id = galleryId
        viewModelScope.launch {
            if (!force && current.showCached(id, page, key)) return@launch
            gated(explicit) {
                viewModelScope.launch { current.translate(id, page, key, request, force) }
            }
        }
    }

    /**
     * Called once per page the reader has settled on, and once for the page it opens on. A page
     * that is already translated is shown in every mode; only the on-turn mode pays for a new one.
     */
    fun onPageSettled(page: Int, settings: ReaderSettings) {
        val request = request(settings)
        val key = TranslationKey.of(request.engine, request.source, request.target)
        val current = runnerFor(key) ?: return
        val state = current.pages.value[page]
        if (state == PageState.Loading || state is PageState.Done) return
        if (settings.translateMode == TranslateMode.OnTurn) {
            translateNow(page, settings, explicit = false)
        } else {
            val id = galleryId
            viewModelScope.launch { current.showCached(id, page, key) }
        }
    }

    fun startWhole(settings: ReaderSettings) {
        val request = request(settings)
        val key = TranslationKey.of(request.engine, request.source, request.target)
        val current = runnerFor(key) ?: return
        val id = galleryId
        val count = pageCount
        gated(explicit = true) { current.startBatch(id, count, key, request) }
    }

    fun cancelWhole() {
        runner.value?.cancelBatch()
    }

    fun acceptConsent() {
        providerStore.update { it.copy(consented = true) }
        declined = false
        _needsConsent.value = false
        val action = pendingAfterConsent
        pendingAfterConsent = null
        action?.invoke()
    }

    fun declineConsent() {
        declined = true
        _needsConsent.value = false
        pendingAfterConsent = null
    }

    fun saveProvider(change: (ProviderSettings) -> ProviderSettings) {
        providerStore.update(change)
    }

    /**
     * Sends a tiny text-only request built from what the dialog holds, saved or not; null means the
     * address, key and model work.
     */
    suspend fun testProvider(baseUrl: String, model: String, apiKey: String): TranslationError? = try {
        client.check(provider.value.edited(baseUrl, model, apiKey))
        null
    } catch (e: TranslationException) {
        e.error
    }

    /**
     * Runs [action] when pages may be sent. A provider that is not set up still runs it: the client
     * refuses without touching the network and the page shows that the provider is missing.
     */
    private fun gated(explicit: Boolean, action: () -> Unit) {
        val settings = provider.value
        if (settings.isConfigured && !settings.consented) {
            if (!explicit && declined) return
            pendingAfterConsent = action
            _needsConsent.value = true
        } else {
            action()
        }
    }

    private fun request(settings: ReaderSettings) = TranslateRequest(
        settings.engine,
        settings.sourceLanguage,
        TargetLanguage.resolve(
            LocaleManager.getSavedLocale(context), Locale.getDefault().language, settings.targetLanguage
        )
    )

    private fun newRunner() =
        TranslationRunner(pageBytes, translator, store, { provider.value.model }, viewModelScope)

    /** A page-only state map must not show results or a batch from different translation choices. */
    private fun runnerFor(key: String): TranslationRunner? {
        val current = runner.value ?: return null
        if (activeKey == key) return current
        current.cancelBatch()
        pendingAfterConsent = null
        _needsConsent.value = false
        activeKey = key
        return newRunner().also { runner.value = it }
    }
}
