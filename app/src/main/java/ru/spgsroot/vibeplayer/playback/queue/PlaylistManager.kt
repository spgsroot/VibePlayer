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

    private val lock = Any()
    private var currentIndex = 0
    private var onCurrentVideoRemovedCallback: ((Video?) -> Unit)? = null

    fun setOnCurrentVideoRemovedListener(callback: (Video?) -> Unit) {
        onCurrentVideoRemovedCallback = callback
    }

    fun current(): Video? = synchronized(lock) {
        _playlist.value.getOrNull(currentIndex)
    }

    fun next(): Video? = synchronized(lock) {
        if (_playlist.value.isEmpty()) return null
        currentIndex = (currentIndex + 1) % _playlist.value.size
        _playlist.value.getOrNull(currentIndex)
    }

    fun previous(): Video? = synchronized(lock) {
        if (_playlist.value.isEmpty()) return null
        currentIndex = if (currentIndex - 1 < 0) _playlist.value.size - 1 else currentIndex - 1
        _playlist.value.getOrNull(currentIndex)
    }

    fun addVideo(video: Video) = synchronized(lock) {
        _playlist.value = _playlist.value + video
    }

    fun removeVideo(video: Video): Boolean {
        var wasCurrent = false
        val nextCurrent = synchronized(lock) {
            val oldList = _playlist.value
            val oldIndex = currentIndex
            val currentId = oldList.getOrNull(oldIndex)?.id
            wasCurrent = video.id == currentId

            val newList = oldList.filter { it.id != video.id }
            _playlist.value = newList

            currentIndex = when {
                newList.isEmpty() -> 0
                wasCurrent && oldIndex >= newList.size -> 0
                wasCurrent -> oldIndex
                currentId != null -> newList.indexOfFirst { it.id == currentId }
                    .takeIf { it >= 0 }
                    ?: oldIndex.coerceIn(0, newList.lastIndex)
                else -> 0
            }

            if (wasCurrent) newList.getOrNull(currentIndex) else null
        }

        if (wasCurrent) {
            // If the removed video was current, notify listener with the already selected replacement.
            onCurrentVideoRemovedCallback?.invoke(nextCurrent)
        }

        return wasCurrent
    }

    fun shuffle() = synchronized(lock) {
        val currentId = _playlist.value.getOrNull(currentIndex)?.id
        val shuffled = _playlist.value.shuffled()
        _playlist.value = shuffled
        currentIndex = currentId
            ?.let { id -> shuffled.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
    }

    fun setPlaylist(videos: List<Video>) = synchronized(lock) {
        val currentId = _playlist.value.getOrNull(currentIndex)?.id
        _playlist.value = videos
        currentIndex = currentId
            ?.let { id -> videos.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
    }

    fun selectVideo(video: Video): Boolean = synchronized(lock) {
        val index = _playlist.value.indexOfFirst { it.id == video.id }
        if (index >= 0) {
            currentIndex = index
            true
        } else {
            false
        }
    }

    fun isEmpty() = synchronized(lock) { _playlist.value.isEmpty() }

    fun getPlaylistOrder(): List<String> = synchronized(lock) {
        _playlist.value.map { it.id.toString() }
    }
}
