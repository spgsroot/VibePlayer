package ru.spgsroot.vibeplayer.playback.timer

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.playback.player.ExoPlayerWrapper
import ru.spgsroot.vibeplayer.playback.queue.PlaylistManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerController @OptIn(UnstableApi::class)
@Inject constructor(
    private val playlistManager: PlaylistManager,
    private val exoPlayerWrapper: ExoPlayerWrapper
) {
    private var timerJob: Job? = null
    private var timerMs: Long = 0
    private var isFullVideoMode: Boolean = false
    private var fullVideoListener: Player.Listener? = null

    fun start(scope: CoroutineScope, timerMs: Long) {
        stop()
        this.timerMs = timerMs
        // Если значение >= 300 секунд (5 мин) или меньше 0, расцениваем как "Полное видео"
        this.isFullVideoMode = timerMs >= 300000L || timerMs <= 0

        if (isFullVideoMode) {
            startFullVideoMode()
        } else {
            startFixedTimerMode(scope)
        }
    }

    @OptIn(UnstableApi::class)
    fun stop() {
        timerJob?.cancel()
        timerJob = null

        fullVideoListener?.let {
            exoPlayerWrapper.getPlayer().removeListener(it)
            fullVideoListener = null
        }
    }

    private fun startFixedTimerMode(scope: CoroutineScope) {
        timerJob = scope.launch {
            while (true) {
                delay(timerMs)
                playNext()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun startFullVideoMode() {
        val player = exoPlayerWrapper.getPlayer()

        fullVideoListener = object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    playNext()
                }
            }
        }

        player.addListener(fullVideoListener!!)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun playNext() {
        val nextVideo = playlistManager.next()
        if (nextVideo != null) {
            exoPlayerWrapper.play(nextVideo)
        }
    }
}
