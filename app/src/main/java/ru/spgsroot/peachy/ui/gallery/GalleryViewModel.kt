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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.repository.VideoRepository
import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.data.repository.PhotoRepository
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
import ru.spgsroot.peachy.data.repository.AlbumRepository
import kotlinx.coroutines.CancellationException


/** What the compression dialog needs: how many videos, their facts once read. */
data class CompressDialogState(val count: Int, val sources: List<SourceInfo>?)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val internalStorageManager: InternalStorageManager,
    private val playlistManager: PlaylistManager,
    private val exoPlayerWrapper: ExoPlayerWrapper,
    private val videoExporter: VideoExporter,
    private val notes: NoteRepository,
    private val compressionManager: CompressionManager,
    private val photoRepository: PhotoRepository,
    private val albumRepository: AlbumRepository
) : ViewModel() {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()
    val albums = albumRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val albumError = MutableStateFlow(false)

    private fun albumAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { albumError.value = true }
        }
    }

    fun createAlbum(name: String, onCreated: (Long) -> Unit = {}) = albumAction { onCreated(albumRepository.create(name)) }
    fun renameAlbum(id: Long, name: String) = albumAction { albumRepository.rename(id, name) }
    fun deleteAlbum(id: Long) = albumAction { albumRepository.delete(id) }
    fun moveToAlbum(chosen: List<GalleryIdentity>, albumId: Long?) = albumAction {
        albumRepository.move(chosen.filter { it.type == GalleryMediaType.Video }.map { it.id },
            chosen.filter { it.type == GalleryMediaType.Photo }.map { it.id }, albumId)
        clearSelection()
    }

    private val _photos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filter = MutableStateFlow(MediaFilter())
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()
    val items: StateFlow<List<GalleryItem>> = combine(_videos, _photos, _query, _filter) { videos, photos, query, filter ->
        galleryItems(videos, photos, query, filter)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedItems = MutableStateFlow<Set<GalleryIdentity>>(emptySet())
    val selectedItems: StateFlow<Set<GalleryIdentity>> = _selectedItems.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedItems.map { it.isNotEmpty() }
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
        viewModelScope.launch {
            photoRepository.getAll().collect { _photos.value = it }
        }
    }

    private fun observeVideos() {
        viewModelScope.launch {
            videoRepository.getAll().collect { videos ->
                _videos.value = videos
            }
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setFilter(filter: MediaFilter) { _filter.value = filter }

    fun toggleSelection(identity: GalleryIdentity) {
        val current = _selectedItems.value
        _selectedItems.value = if (identity in current) current - identity else current + identity
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    private fun selectedVideos(): List<Video> {
        val selected = _selectedItems.value
        if (!selected.allowsVideoActions()) return emptyList()
        return _videos.value.filter { GalleryIdentity(GalleryMediaType.Video, it.id) in selected }
    }

    private val _compressDialog = MutableStateFlow<CompressDialogState?>(null)
    /** Non-null while the compression dialog is open. */
    val compressDialog: StateFlow<CompressDialogState?> = _compressDialog.asStateFlow()
    private var compressionTargets: List<Video> = emptyList()

    val compression: StateFlow<CompressionState> = compressionManager.state

    /** Opens the dialog at once and fills in the facts about the selected files as they are read. */
    fun openCompress(chosen: List<Video>) {
        if (chosen.isEmpty() || compression.value is CompressionState.Running) return
        compressionTargets = chosen.toList()
        _compressDialog.value = CompressDialogState(chosen.size, null)
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { chosen.mapNotNull { VideoProbe.read(File(it.filePath)) } }
            _compressDialog.value = _compressDialog.value?.copy(sources = sources)
        }
    }

    fun closeCompress() {
        _compressDialog.value = null
        compressionTargets = emptyList()
    }

    fun compressSelected(settings: CompressionSettings) {
        val chosen = compressionTargets
        _compressDialog.value = null
        compressionTargets = emptyList()
        if (chosen.isEmpty()) return
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
        val items = selectedVideos().map { ExportItem(it.title, it.filePath) }
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

    private val _deleteFailures = MutableStateFlow(0)
    val deleteFailures: StateFlow<Int> = _deleteFailures.asStateFlow()

    fun consumeDeleteFailures() {
        _deleteFailures.value = 0
    }

    fun deleteSelected() {
        val selected = _selectedItems.value
        val chosen = galleryItems(_videos.value, _photos.value, "").filter { it.identity in selected }
        deleteItems(chosen)
    }

    fun deleteItem(item: GalleryItem) {
        deleteItems(listOf(item))
    }

    private fun deleteItems(chosen: List<GalleryItem>) {
        viewModelScope.launch {
            val failed = deleteGalleryItems(chosen, delete = ::deleteItemNow, onDeleted = {
                _selectedItems.value -= it
            })
            // Include failed single-card deletions in selection too, so Delete can retry them.
            _selectedItems.value += failed
            _deleteFailures.value = failed.size
        }
    }

    private suspend fun deleteItemNow(item: GalleryItem) {
        when (item) {
            is GalleryItem.VideoItem -> deleteVideo(item.video)
            is GalleryItem.PhotoItem -> photoRepository.delete(item.photo)
        }
    }

    fun renamePhoto(photo: PhotoEntity, newTitle: String) {
        viewModelScope.launch {
            photoRepository.update(photo.copy(title = newTitle))
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
        playlistManager.selectVideo(video)
        exoPlayerWrapper.play(video)
    }
}
