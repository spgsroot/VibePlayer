package ru.spgsroot.vibeplayer.ui.gallery

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
import ru.spgsroot.vibeplayer.data.repository.VideoRepository
import ru.spgsroot.vibeplayer.data.storage.InternalStorageManager
import ru.spgsroot.vibeplayer.domain.model.Video
import ru.spgsroot.vibeplayer.playback.queue.PlaylistManager
import ru.spgsroot.vibeplayer.playback.player.ExoPlayerWrapper
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val internalStorageManager: InternalStorageManager,
    private val playlistManager: PlaylistManager,
    private val exoPlayerWrapper: ExoPlayerWrapper
) : ViewModel() {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _selectedVideoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedVideoIds: StateFlow<Set<Long>> = _selectedVideoIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedVideoIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

            // Delete from storage (video + thumbnail)
            internalStorageManager.delete(video.id)

            // Delete from database
            videoRepository.deleteById(video.id)
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
        exoPlayerWrapper.play(video)
    }
}