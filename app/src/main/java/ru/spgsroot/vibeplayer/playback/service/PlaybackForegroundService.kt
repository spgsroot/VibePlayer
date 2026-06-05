package ru.spgsroot.vibeplayer.playback.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.data.repository.PlaybackStateRepository
import ru.spgsroot.vibeplayer.domain.model.PlaybackState
import ru.spgsroot.vibeplayer.playback.player.ExoPlayerWrapper
import ru.spgsroot.vibeplayer.playback.player.PlayerState
import ru.spgsroot.vibeplayer.playback.queue.PlaylistManager
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackForegroundService : Service() {

    @Inject lateinit var exoPlayerWrapper: ExoPlayerWrapper
    @Inject lateinit var playlistManager: PlaylistManager
    @Inject lateinit var playbackStateRepository: PlaybackStateRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = PlaybackBinder()

    inner class PlaybackBinder : Binder() {
        fun getService(): PlaybackForegroundService = this@PlaybackForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observePlayerState()
        observePeriodicPlaybackStateSave()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> exoPlayerWrapper.play()
            ACTION_PAUSE -> {
                exoPlayerWrapper.pause()
                savePlaybackStateAsync()
            }
            ACTION_NEXT -> playlistManager.next()?.let {
                exoPlayerWrapper.play(it)
                savePlaybackStateAsync()
            }
            ACTION_STOP -> {
                savePlaybackStateAsync()
                stopSelf()
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification(exoPlayerWrapper.state.value))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        savePlaybackStateAsync()
        exoPlayerWrapper.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observePlayerState() {
        exoPlayerWrapper.state.onEach { state ->
            updateNotification(state)
        }.launchIn(serviceScope)
    }

    private fun observePeriodicPlaybackStateSave() {
        serviceScope.launch {
            while (isActive) {
                delay(PLAYBACK_STATE_SAVE_INTERVAL_MS)
                savePlaybackStateAsync()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Video playback controls"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(state: PlayerState): Notification {
        val currentVideo = playlistManager.current()
        val title = currentVideo?.title ?: "VibePlayer"

        val (contentText, playPauseAction, playPauseLabel) = when (state) {
            is PlayerState.Playing -> Triple("Playing", ACTION_PAUSE, "Pause")
            is PlayerState.Paused -> Triple("Paused", ACTION_PLAY, "Play")
            else -> Triple("Ready", ACTION_PLAY, "Play")
        }

        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, PlaybackForegroundService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackForegroundService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(R.drawable.ic_launcher_foreground, playPauseLabel, playPauseIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Next", nextIntent)
            .build()
    }

    private fun updateNotification(state: PlayerState) {
        val notification = buildNotification(state)
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun savePlaybackStateAsync() {
        val playbackState = buildPlaybackState() ?: return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            withContext(NonCancellable) {
                runCatching {
                    playbackStateRepository.saveState(playbackState)
                }
            }
        }
    }

    private fun buildPlaybackState(): PlaybackState? {
        val currentVideo = playlistManager.current() ?: return null
        val position = exoPlayerWrapper.getCurrentPosition()
        val playlistOrder = playlistManager.getPlaylistOrder()

        return PlaybackState(
            currentVideoId = currentVideo.id,
            positionMs = position,
            playlistOrder = playlistOrder
        )
    }

    companion object {
        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 1
        private const val PLAYBACK_STATE_SAVE_INTERVAL_MS = 10_000L
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
