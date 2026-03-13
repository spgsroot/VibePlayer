package ru.spgsroot.vibeplayer.playback.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.spgsroot.vibeplayer.domain.dsp.AudioAnalyzer
import ru.spgsroot.vibeplayer.domain.dsp.HapticMapper
import ru.spgsroot.vibeplayer.domain.model.Video
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ExoPlayerWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioAnalyzer: AudioAnalyzer,
    private val hapticMapper: HapticMapper
) : Player.Listener {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state

    private var currentVideoId: Long? = null

    private val dspAudioProcessor = DspAudioProcessor(audioAnalyzer, hapticMapper)

    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setRenderersFactory(DspRenderersFactory(context, dspAudioProcessor))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                addListener(this@ExoPlayerWrapper)
            }
    }

    fun play(video: Video) {
        currentVideoId = video.id
        val mediaItem = MediaItem.fromUri(video.filePath)
        
        // Reset to beginning before playing new video
        player.seekTo(0)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        _state.value = PlayerState.Playing(video)
    }

    fun pause() {
        player.pause()
        val currentState = _state.value
        if (currentState is PlayerState.Playing) {
            _state.value = PlayerState.Paused(currentState.video, player.currentPosition)
        }
    }

    fun resume() {
        player.play()
        val currentState = _state.value
        if (currentState is PlayerState.Paused) {
            _state.value = PlayerState.Playing(currentState.video)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun release() {
        player.release()
    }

    fun getPlayer(): Player = player

    fun play() {
        player.play()
        val currentState = _state.value
        if (currentState is PlayerState.Paused) {
            _state.value = PlayerState.Playing(currentState.video)
        }
    }

    fun getCurrentPosition(): Long = player.currentPosition

    override fun onPlayerError(error: PlaybackException) {
        val errorMessage = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Network error"
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "Video format not supported"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "File not found"
            else -> "Playback error: ${error.message}"
        }
        _state.value = PlayerState.Error(errorMessage)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            _state.value = PlayerState.Idle
        }
    }
}
