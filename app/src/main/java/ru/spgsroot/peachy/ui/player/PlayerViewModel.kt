package ru.spgsroot.peachy.ui.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.downloader.ExternalDownloader
import ru.spgsroot.peachy.data.downloader.completeDownloadedVideo
import ru.spgsroot.peachy.data.hosts.HostEntry
import ru.spgsroot.peachy.data.hosts.HostImporter
import ru.spgsroot.peachy.data.repository.SettingsRepository
import ru.spgsroot.peachy.data.repository.VideoRepository
import ru.spgsroot.peachy.data.storage.InternalStorageManager
import ru.spgsroot.peachy.data.storage.VideoImporter
import ru.spgsroot.peachy.data.storage.VideoExporter
import ru.spgsroot.peachy.data.storage.ExportItem
import ru.spgsroot.peachy.domain.model.Settings
import ru.spgsroot.peachy.domain.model.Video
import ru.spgsroot.peachy.playback.player.ExoPlayerWrapper
import ru.spgsroot.peachy.playback.player.PlayerState
import ru.spgsroot.peachy.playback.queue.PlaylistManager
import ru.spgsroot.peachy.playback.service.WebViewAudioCaptureService
import ru.spgsroot.peachy.playback.timer.TimerController
import java.io.File
import java.io.IOException
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistManager: PlaylistManager,
    val exoPlayerWrapper: ExoPlayerWrapper,
    private val settingsRepository: SettingsRepository,
    private val timerController: TimerController,
    private val videoRepository: VideoRepository,
    private val storageManager: InternalStorageManager,
    private val externalDownloader: ExternalDownloader,
    private val videoImporter: VideoImporter,
    private val hostImporter: HostImporter,
    private val videoExporter: VideoExporter
) : ViewModel() {

    private var currentSettings: Settings? = null

    val videos: StateFlow<List<Video>> = videoRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Empty)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val playerState: StateFlow<PlayerState> = exoPlayerWrapper.state

    init {
        observePlaylist()
        observeSettings()
        loadVideosToPlaylist()
        setupCurrentVideoRemovedListener()
    }

    private fun setupCurrentVideoRemovedListener() {
        playlistManager.setOnCurrentVideoRemovedListener { nextVideo ->
            viewModelScope.launch {
                nextVideo?.let {
                    stopWebViewCapture()
                    exoPlayerWrapper.play(nextVideo)
                    startTimer()
                } ?: run {
                    exoPlayerWrapper.pause()
                    timerController.stop()
                }
            }
        }
    }

    private fun loadVideosToPlaylist() {
        viewModelScope.launch {
            videos.collectLatest { playlist ->
                playlistManager.setPlaylist(playlist)
            }
        }
    }

    private fun observePlaylist() {
        viewModelScope.launch {
            playlistManager.playlist.collectLatest { playlist ->
                _uiState.value = if (playlist.isEmpty()) {
                    PlayerUiState.Empty
                } else {
                    PlayerUiState.Ready
                }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().collectLatest { settings ->
                currentSettings = settings
                settings?.let {
                    exoPlayerWrapper.setSpeed(it.playbackSpeed)
                    if (playerState.value is PlayerState.Playing) {
                        timerController.start(viewModelScope, it.timerMs)
                    }
                }
            }
        }
    }

    fun onPlayPause() {
        when (val state = playerState.value) {
            is PlayerState.Playing -> {
                exoPlayerWrapper.pause()
                timerController.stop()
            }

            is PlayerState.Paused -> {
                stopWebViewCapture()
                exoPlayerWrapper.play()
                startTimer()
            }

            is PlayerState.Idle -> {
                playlistManager.current()?.let {
                    stopWebViewCapture()
                    exoPlayerWrapper.play(it)
                    startTimer()
                }
            }

            else -> Unit
        }
    }

    fun onNext() {
        playlistManager.next()?.let {
            stopWebViewCapture()
            exoPlayerWrapper.play(it)
            startTimer()
        }
    }

    fun onPrevious() {
        playlistManager.previous()?.let {
            stopWebViewCapture()
            exoPlayerWrapper.play(it)
            startTimer()
        }
    }

    fun onSeek(positionMs: Long) {
        exoPlayerWrapper.seekTo(positionMs)
    }

    fun pauseForWebView() {
        if (playerState.value is PlayerState.Playing) {
            exoPlayerWrapper.pause()
            timerController.stop()
        }
    }

    fun playVideo(video: Video) {
        playlistManager.selectVideo(video)
        stopWebViewCapture()
        exoPlayerWrapper.play(video)
        startTimer()
    }

    fun getVideoById(id: Long): Video? = videos.value.find { it.id == id }

    private fun startTimer() {
        currentSettings?.let {
            timerController.start(viewModelScope, it.timerMs)
        }
    }

    private fun stopWebViewCapture() {
        runCatching {
            context.stopService(WebViewAudioCaptureService.stopIntent(context))
        }
    }

    fun importVideoFromUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                val fileName = "${System.currentTimeMillis()}.mp4"
                val savedFile = storageManager.save(inputStream, fileName)
                videoImporter.persist(savedFile)
            }
        }
    }

    fun downloadVideoFromUrl(url: String) {
        viewModelScope.launch {
            importVideoFromUrl(url)
        }
    }

    /**
     * Imports [url]. A link of a supported file host brings every video it points to (all videos of an album);
     * the result is a success when at least one of them was saved.
     */
    suspend fun importVideoFromUrl(url: String): Result<Unit> =
        importVideoFromUrlWithFolder(url, null).map { Unit }

    /** The returned count is a copy warning, never a failed library import. */
    suspend fun importVideoFromUrlWithFolder(url: String, folderUri: String?): Result<Int> =
        withContext(Dispatchers.IO) {
            val normalizedUrl = url.trim()
            if (normalizedUrl.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Пустой URL"))
            }
            val copy: ((File) -> Unit)? = folderUri?.let { tree ->
                { file ->
                    val exported = videoExporter.export(
                        Uri.parse(tree), listOf(ExportItem(file.nameWithoutExtension, file.path))
                    )
                    if (exported.failed.isNotEmpty()) throw IOException("Folder copy failed")
                }
            }
            val results = importItemsFor(normalizedUrl, copy).map { it.run() }
            val result = results.firstOrNull { it.isSuccess } ?: results.first()
            result.map { results.sumOf { it.getOrDefault(0) } }
        }

    fun downloadVideosFromUrls(urls: List<String>) {
        viewModelScope.launch {
            importVideosFromUrls(urls)
        }
    }

    suspend fun importVideosFromUrls(
        urls: List<String>,
        onProgress: (BatchImportProgress) -> Unit = {}
    ): BatchImportSummary = withContext(Dispatchers.IO) {
        val normalizedUrls = urls
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        // Album links expand to one item per video, so the totals count videos, not links.
        val items = normalizedUrls.flatMap { importItemsFor(it) }

        val failedUrls = mutableListOf<String>()
        var succeeded = 0
        var failed = 0

        onProgress(
            BatchImportProgress(
                total = items.size,
                completed = 0,
                currentUrl = "",
                succeeded = 0,
                failed = 0
            )
        )

        items.forEachIndexed { index, item ->
            val result = item.run()

            if (result.isSuccess) {
                succeeded++
            } else {
                failed++
                failedUrls += item.label
            }

            onProgress(
                BatchImportProgress(
                    total = items.size,
                    completed = index + 1,
                    currentUrl = item.label,
                    succeeded = succeeded,
                    failed = failed,
                    lastError = result.exceptionOrNull()?.message
                )
            )
        }

        BatchImportSummary(
            total = items.size,
            succeeded = succeeded,
            failed = failed,
            failedUrls = failedUrls
        )
    }

    private class ImportItem(val label: String, val run: suspend () -> Result<Int>)

    /** Never throws: a host link that cannot be read becomes one failing item, so batches carry on. */
    private fun importItemsFor(url: String, copy: ((File) -> Unit)? = null): List<ImportItem> {
        if (!hostImporter.recognizes(url)) {
            return listOf(ImportItem(url) { videoImporter.importUrlWithCopy(url, copy = copy) })
        }
        val entries = try {
            hostImporter.expand(url)
        } catch (e: Exception) {
            return listOf(ImportItem(url) { Result.failure(e) })
        }
        return entries.map { entry -> ImportItem(entry.label) { importHostEntry(entry, copy) } }
    }


    private suspend fun importHostEntry(entry: HostEntry, copy: ((File) -> Unit)?): Result<Int> {
        val link = try {
            hostImporter.resolve(entry)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return externalDownloader.download(
            url = link.url,
            fileName = "${System.currentTimeMillis()}.${link.name.substringAfterLast('.', "mp4").lowercase()}",
            headers = link.headers
        ).fold(
            onSuccess = { file ->
                completeDownloadedVideo(file,
                    save = { videoImporter.persist(file, title = link.name.substringBeforeLast('.')) },
                    copy = copy)
            },
            onFailure = { Result.failure(it) }
        )
    }


    override fun onCleared() {
        timerController.stop()
        super.onCleared()
    }
}

sealed interface PlayerUiState {
    data object Empty : PlayerUiState
    data object Ready : PlayerUiState
}
