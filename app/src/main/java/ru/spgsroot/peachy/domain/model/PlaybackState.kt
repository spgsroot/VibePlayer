package ru.spgsroot.peachy.domain.model

data class PlaybackState(
    val currentVideoId: Long?,
    val positionMs: Long,
    val playlistOrder: List<String>
)
