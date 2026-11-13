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
import ru.spgsroot.peachy.manga.TranslateMode
import ru.spgsroot.peachy.translate.BatchState
import ru.spgsroot.peachy.translate.PageBytes
import ru.spgsroot.peachy.translate.PageState
import ru.spgsroot.peachy.translate.ProviderSettings
import ru.spgsroot.peachy.translate.ProviderSettingsStore
import ru.spgsroot.peachy.translate.TargetLanguage
import ru.spgsroot.peachy.translate.TranslationClient
import ru.spgsroot.peachy.translate.TranslationError
import ru.spgsroot.peachy.translate.TranslationException
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
    private val client: TranslationClient
) : ViewModel() {
    private val runner = MutableStateFlow<TranslationRunner?>(null)
    private var galleryId = -1
    private var pageCount = 0
    private var pendingAfterConsent: (() -> Unit)? = null

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
        _needsConsent.value = false
        this.galleryId = galleryId
        pageCount = pagePaths.size
        pageBytes.bind(pagePaths)
        runner.value = TranslationRunner(pageBytes, client, store, { provider.value.model }, viewModelScope)
    }

    fun translateNow(page: Int, force: Boolean = false) {
        val current = runner.value ?: return
        val id = galleryId
        gated {
            viewModelScope.launch { current.translate(id, page, language(), force) }
        }
    }

    /** Called once per page the reader has settled on; only the on-turn mode acts. */
    fun onPageSettled(page: Int, mode: TranslateMode) {
        if (mode != TranslateMode.OnTurn) return
        val state = pages.value[page]
        if (state == PageState.Loading || state is PageState.Done) return
        translateNow(page)
    }

    fun startWhole() {
        val current = runner.value ?: return
        val id = galleryId
        val count = pageCount
        gated { current.startBatch(id, count, language()) }
    }

    fun cancelWhole() {
        runner.value?.cancelBatch()
    }

    fun acceptConsent() {
        providerStore.update { it.copy(consented = true) }
        _needsConsent.value = false
        val action = pendingAfterConsent
        pendingAfterConsent = null
        action?.invoke()
    }

    fun declineConsent() {
        _needsConsent.value = false
        pendingAfterConsent = null
    }

    fun saveProvider(change: (ProviderSettings) -> ProviderSettings) {
        providerStore.update(change)
    }

    /** Sends a tiny text-only request; null means the address, key and model work. */
    suspend fun testProvider(): TranslationError? = try {
        client.check()
        null
    } catch (e: TranslationException) {
        e.error
    }

    /**
     * Runs [action] when pages may be sent. A provider that is not set up still runs it: the client
     * refuses without touching the network and the page shows that the provider is missing.
     */
    private fun gated(action: () -> Unit) {
        val settings = provider.value
        if (settings.isConfigured && !settings.consented) {
            pendingAfterConsent = action
            _needsConsent.value = true
        } else {
            action()
        }
    }

    private fun language(): String =
        TargetLanguage.resolve(LocaleManager.getSavedLocale(context), Locale.getDefault().language)
}
