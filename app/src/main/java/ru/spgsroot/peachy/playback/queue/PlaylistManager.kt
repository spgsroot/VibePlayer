package ru.spgsroot.peachy.playback.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.spgsroot.peachy.domain.model.Video
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistManager @Inject constructor() {
    private val _playlist = MutableStateFlow<List<Video>>(emptyList())
    val playlist: StateFlow<List<Video>> = _playlist

    private val lock = Any()
    // Playback may remain outside the filtered queue until the user advances.
    private var currentVideo: Video? = null
    private var onCurrentVideoRemovedCallback: ((Video?) -> Unit)? = null

    fun setOnCurrentVideoRemovedListener(callback: (Video?) -> Unit) {
        onCurrentVideoRemovedCallback = callback
    }

    fun current(): Video? = synchronized(lock) {
        currentVideo
    }

    fun next(): Video? = synchronized(lock) {
        val videos = _playlist.value
        if (videos.isEmpty()) return null
        val index = videos.indexOfFirst { it.id == currentVideo?.id }
        videos[(index + 1) % videos.size].also { currentVideo = it }
    }

    fun previous(): Video? = synchronized(lock) {
        val videos = _playlist.value
        if (videos.isEmpty()) return null
        val index = videos.indexOfFirst { it.id == currentVideo?.id }
        videos[if (index <= 0) videos.lastIndex else index - 1].also { currentVideo = it }
    }

    fun addVideo(video: Video) = synchronized(lock) {
        _playlist.value = _playlist.value + video
        if (currentVideo == null) currentVideo = video
    }

    fun removeVideo(video: Video): Boolean {
        val replacement = synchronized(lock) {
            val oldList = _playlist.value
            val oldIndex = oldList.indexOfFirst { it.id == currentVideo?.id }
            val wasCurrent = video.id == currentVideo?.id
            val newList = oldList.filter { it.id != video.id }
            _playlist.value = newList
            if (!wasCurrent) return false
            val next = newList.getOrNull(oldIndex.coerceAtLeast(0)) ?: newList.firstOrNull()
            currentVideo = next
            next
        }
        onCurrentVideoRemovedCallback?.invoke(replacement)
        return true
    }

    fun shuffle() = synchronized(lock) {
        _playlist.value = _playlist.value.shuffled()
    }

    fun setPlaylist(videos: List<Video>) = synchronized(lock) {
        _playlist.value = videos
        currentVideo = if (currentVideo == null) videos.firstOrNull()
            else videos.firstOrNull { it.id == currentVideo?.id } ?: currentVideo
    }

    fun selectVideo(video: Video) = synchronized(lock) {
        currentVideo = video
    }

    fun isEmpty() = synchronized(lock) { _playlist.value.isEmpty() }

    fun getPlaylistOrder(): List<String> = synchronized(lock) {
        _playlist.value.map { it.id.toString() }
    }
}
