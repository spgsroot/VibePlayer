package ru.spgsroot.vibeplayer.ui.player

import android.content.Context
import android.media.MediaMetadataRetriever
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
import ru.spgsroot.vibeplayer.data.downloader.ExternalDownloader
import ru.spgsroot.vibeplayer.data.repository.SettingsRepository
import ru.spgsroot.vibeplayer.data.repository.VideoRepository
import ru.spgsroot.vibeplayer.data.storage.InternalStorageManager
import ru.spgsroot.vibeplayer.domain.model.Settings
import ru.spgsroot.vibeplayer.domain.model.Video
import ru.spgsroot.vibeplayer.playback.player.ExoPlayerWrapper
import ru.spgsroot.vibeplayer.playback.player.PlayerState
import ru.spgsroot.vibeplayer.playback.queue.PlaylistManager
import ru.spgsroot.vibeplayer.playback.timer.TimerController
import java.io.File
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
    private val externalDownloader: ExternalDownloader
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
        playlistManager.setOnCurrentVideoRemovedListener {
            viewModelScope.launch {
                playlistManager.next()?.let { nextVideo ->
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
                exoPlayerWrapper.play()
                startTimer()
            }

            is PlayerState.Idle -> {
                playlistManager.current()?.let {
                    exoPlayerWrapper.play(it)
                    startTimer()
                }
            }

            else -> Unit
        }
    }

    fun onNext() {
        playlistManager.next()?.let {
            exoPlayerWrapper.play(it)
            startTimer()
        }
    }

    fun onPrevious() {
        playlistManager.previous()?.let {
            exoPlayerWrapper.play(it)
            startTimer()
        }
    }

    fun onSeek(positionMs: Long) {
        exoPlayerWrapper.seekTo(positionMs)
    }

    fun playVideo(video: Video) {
        exoPlayerWrapper.play(video)
        startTimer()
    }

    fun getVideoById(id: Long): Video? = videos.value.find { it.id == id }

    private fun startTimer() {
        currentSettings?.let {
            timerController.start(viewModelScope, it.timerMs)
        }
    }

    fun importVideoFromUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                val fileName = "${System.currentTimeMillis()}.mp4"
                val savedFile = storageManager.save(inputStream, fileName)
                persistSavedVideo(savedFile)
            }
        }
    }

    fun downloadVideoFromUrl(url: String) {
        viewModelScope.launch {
            importVideoFromUrl(url)
        }
    }

    suspend fun importVideoFromUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Пустой URL"))
        }

        val fileName = buildDownloadFileName(normalizedUrl)

        externalDownloader.download(
            url = normalizedUrl,
            fileName = fileName
        ).fold(
            onSuccess = { savedFile ->
                persistSavedVideo(savedFile)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
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

        val failedUrls = mutableListOf<String>()
        var succeeded = 0
        var failed = 0

        onProgress(
            BatchImportProgress(
                total = normalizedUrls.size,
                completed = 0,
                currentUrl = "",
                succeeded = 0,
                failed = 0
            )
        )

        normalizedUrls.forEachIndexed { index, currentUrl ->
            val result = importVideoFromUrl(currentUrl)

            if (result.isSuccess) {
                succeeded++
            } else {
                failed++
                failedUrls += currentUrl
            }

            onProgress(
                BatchImportProgress(
                    total = normalizedUrls.size,
                    completed = index + 1,
                    currentUrl = currentUrl,
                    succeeded = succeeded,
                    failed = failed,
                    lastError = result.exceptionOrNull()?.message
                )
            )
        }

        BatchImportSummary(
            total = normalizedUrls.size,
            succeeded = succeeded,
            failed = failed,
            failedUrls = failedUrls
        )
    }

    private suspend fun persistSavedVideo(savedFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = extractMetadata(savedFile)
            val video = Video(
                id = 0,
                title = metadata.title,
                filePath = savedFile.absolutePath,
                duration = metadata.duration,
                thumbnailPath = null,
                addedAt = System.currentTimeMillis(),
                fileSize = savedFile.length(),
                isCorrupted = false
            )
            videoRepository.insert(video)
            Unit
        }.onFailure {
            savedFile.delete()
        }
    }

    private fun buildDownloadFileName(url: String): String {
        val lastSegment = runCatching { Uri.parse(url).lastPathSegment.orEmpty() }
            .getOrDefault("")

        val extension = lastSegment
            .substringAfterLast('.', "mp4")
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            ?: "mp4"

        return "${System.currentTimeMillis()}.$extension"
    }

    private fun extractMetadata(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val title = file.nameWithoutExtension
            VideoMetadata(
                title = title,
                duration = duration
            )
        } finally {
            retriever.release()
        }
    }

    override fun onCleared() {
        timerController.stop()
        super.onCleared()
    }
}

data class VideoMetadata(
    val title: String,
    val duration: Long
)

sealed interface PlayerUiState {
    data object Empty : PlayerUiState
    data object Ready : PlayerUiState
}
