package ru.spgsroot.peachy.ui.gallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.repository.VideoRepository
import ru.spgsroot.peachy.data.storage.InternalStorageManager
import ru.spgsroot.peachy.domain.model.Video
import ru.spgsroot.peachy.playback.queue.PlaylistManager
import ru.spgsroot.peachy.data.storage.ExportItem
import ru.spgsroot.peachy.data.storage.ExportResult
import ru.spgsroot.peachy.data.storage.VideoExporter
import ru.spgsroot.peachy.data.notes.NoteKind
import ru.spgsroot.peachy.data.notes.NoteRepository
import ru.spgsroot.peachy.playback.player.ExoPlayerWrapper
import ru.spgsroot.peachy.compress.CompressionManager
import ru.spgsroot.peachy.compress.CompressionSettings
import ru.spgsroot.peachy.compress.CompressionState
import ru.spgsroot.peachy.compress.SourceInfo
import ru.spgsroot.peachy.compress.VideoProbe
import java.io.File
import javax.inject.Inject


/** What the compression dialog needs: how many videos, their facts once read. */
data class CompressDialogState(val count: Int, val sources: List<SourceInfo>?)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val internalStorageManager: InternalStorageManager,
    private val playlistManager: PlaylistManager,
    private val exoPlayerWrapper: ExoPlayerWrapper,
    private val videoExporter: VideoExporter,
    private val notes: NoteRepository,
    private val compressionManager: CompressionManager
) : ViewModel() {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _selectedVideoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedVideoIds: StateFlow<Set<Long>> = _selectedVideoIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedVideoIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Text of each video's note, by video id; videos without a note are absent. */
    val videoNotes: StateFlow<Map<Long, String>> = notes.observeAll()
        .map { rows ->
            rows.filter { it.kind == NoteKind.VIDEO }
                .mapNotNull { row -> row.ref.toLongOrNull()?.let { it to row.text } }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun saveNote(videoId: Long, text: String) {
        viewModelScope.launch { notes.save(NoteKind.VIDEO, videoId.toString(), text) }
    }

    init {
        observeVideos()
    }

    private fun observeVideos() {
        viewModelScope.launch {
            videoRepository.getAll().collect { videos ->
                _videos.value = videos
            }
        }
    }

    fun toggleSelection(videoId: Long) {
        val current = _selectedVideoIds.value.toMutableSet()
        if (current.contains(videoId)) {
            current.remove(videoId)
        } else {
            current.add(videoId)
        }
        _selectedVideoIds.value = current
    }

    fun clearSelection() {
        _selectedVideoIds.value = emptySet()
    }

    fun selectAll() {
        _selectedVideoIds.value = _videos.value.map { it.id }.toSet()
    }

    private val _compressDialog = MutableStateFlow<CompressDialogState?>(null)
    /** Non-null while the compression dialog is open. */
    val compressDialog: StateFlow<CompressDialogState?> = _compressDialog.asStateFlow()

    val compression: StateFlow<CompressionState> = compressionManager.state

    /** Opens the dialog at once and fills in the facts about the selected files as they are read. */
    fun openCompress() {
        val chosen = _videos.value.filter { it.id in _selectedVideoIds.value }
        if (chosen.isEmpty() || compression.value is CompressionState.Running) return
        _compressDialog.value = CompressDialogState(chosen.size, null)
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { chosen.mapNotNull { VideoProbe.read(File(it.filePath)) } }
            _compressDialog.value = _compressDialog.value?.copy(sources = sources)
        }
    }

    fun closeCompress() {
        _compressDialog.value = null
    }

    fun compressSelected(settings: CompressionSettings) {
        val chosen = _videos.value.filter { it.id in _selectedVideoIds.value }
        _compressDialog.value = null
        compressionManager.start(chosen, settings)
        clearSelection()
    }

    fun cancelCompression() = compressionManager.cancel()

    fun consumeCompressionResult() = compressionManager.consumeFinished()

    private val _exportProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** (done, total) while an export runs, null otherwise. */
    val exportProgress: StateFlow<Pair<Int, Int>?> = _exportProgress.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    fun consumeExportResult() {
        _exportResult.value = null
    }

    /** Copies the selected videos into the folder behind [treeUri]; the selection is kept. */
    fun exportSelected(treeUri: Uri) {
        if (_exportProgress.value != null) return
        val items = _videos.value
            .filter { it.id in _selectedVideoIds.value }
            .map { ExportItem(it.title, it.filePath) }
        if (items.isEmpty()) return
        _exportProgress.value = 0 to items.size
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                videoExporter.export(treeUri, items) { done, total ->
                    _exportProgress.value = done to total
                }
            }
            _exportProgress.value = null
            _exportResult.value = result
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val idsToDelete = _selectedVideoIds.value.toList()
            for (id in idsToDelete) {
                val video = _videos.value.find { it.id == id }
                video?.let {
                    deleteVideo(it)
                }
            }
            clearSelection()
        }
    }

    private suspend fun deleteVideo(video: Video) {
        withContext(Dispatchers.IO) {
            // Remove from playlist first
            playlistManager.removeVideo(video)

            internalStorageManager.deleteFile(video.filePath)
            internalStorageManager.delete(video.id)
            // Delete from database
            videoRepository.deleteById(video.id)
            notes.delete(NoteKind.VIDEO, video.id.toString())
        }
    }

    fun renameVideo(video: Video, newTitle: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val updatedVideo = video.copy(title = newTitle)
                videoRepository.update(updatedVideo)
            }
        }
    }

    fun updateThumbnail(video: Video, uri: Uri, context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Delete old thumbnail if exists
                    video.thumbnailPath?.let { oldPath ->
                        internalStorageManager.deleteThumbnail(oldPath)
                    }

                    // Save new thumbnail
                    val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                    val thumbnailPath = internalStorageManager.saveThumbnail(inputStream, video.id)

                    // Update video with new thumbnail path
                    val updatedVideo = video.copy(thumbnailPath = thumbnailPath)
                    videoRepository.update(updatedVideo)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getVideoById(id: Long): Video? {
        return _videos.value.find { it.id == id }
    }

    fun playVideo(video: Video) {
        if (!playlistManager.selectVideo(video)) {
            val playlist = if (_videos.value.any { it.id == video.id }) {
                _videos.value
            } else {
                _videos.value + video
            }
            playlistManager.setPlaylist(playlist)
            playlistManager.selectVideo(video)
        }
        exoPlayerWrapper.play(video)
    }
}
