package ru.spgsroot.vibeplayer.playback.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.spgsroot.vibeplayer.domain.model.Video
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistManager @Inject constructor() {
    private val _playlist = MutableStateFlow<List<Video>>(emptyList())
    val playlist: StateFlow<List<Video>> = _playlist

    private var currentIndex = 0
    private var onCurrentVideoRemovedCallback: ((Video) -> Unit)? = null

    fun setOnCurrentVideoRemovedListener(callback: (Video) -> Unit) {
        onCurrentVideoRemovedCallback = callback
    }

    fun current(): Video? = _playlist.value.getOrNull(currentIndex)

    fun next(): Video? {
        if (_playlist.value.isEmpty()) return null
        currentIndex = (currentIndex + 1) % _playlist.value.size
        return current()
    }

    fun previous(): Video? {
        if (_playlist.value.isEmpty()) return null
        currentIndex = if (currentIndex - 1 < 0) _playlist.value.size - 1 else currentIndex - 1
        return current()
    }

    fun addVideo(video: Video) {
        _playlist.value = _playlist.value + video
    }

    fun removeVideo(video: Video): Boolean {
        val wasCurrent = video.id == current()?.id
        val newList = _playlist.value.filter { it.id != video.id }
        _playlist.value = newList
        
        if (wasCurrent) {
            // If the removed video was current, notify listener and move to next
            onCurrentVideoRemovedCallback?.invoke(video)
            return true
        }
        
        if (currentIndex >= newList.size) {
            currentIndex = (newList.size - 1).coerceAtLeast(0)
        }
        return false
    }

    fun shuffle() {
        _playlist.value = _playlist.value.shuffled()
        currentIndex = 0
    }

    fun setPlaylist(videos: List<Video>) {
        _playlist.value = videos
        currentIndex = 0
    }

    fun isEmpty() = _playlist.value.isEmpty()

    fun getPlaylistOrder(): List<String> = _playlist.value.map { it.id.toString() }
}
