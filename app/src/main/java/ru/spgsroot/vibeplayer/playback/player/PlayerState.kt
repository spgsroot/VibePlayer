package ru.spgsroot.vibeplayer.playback.player

import ru.spgsroot.vibeplayer.domain.model.Video

sealed interface PlayerState {
    data object Idle : PlayerState
    data class Playing(val video: Video) : PlayerState
    data class Paused(val video: Video, val position: Long) : PlayerState
    data class Switching(val from: Video, val to: Video) : PlayerState
    data class Error(val reason: String) : PlayerState
}
