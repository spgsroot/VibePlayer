package ru.spgsroot.vibeplayer.playback.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackServiceConnection(private val context: Context) {

    private val _service = MutableStateFlow<PlaybackForegroundService?>(null)
    val service: StateFlow<PlaybackForegroundService?> = _service.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val playbackBinder = binder as? PlaybackForegroundService.PlaybackBinder
            _service.value = playbackBinder?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    fun bind() {
        val intent = Intent(context, PlaybackForegroundService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        context.unbindService(connection)
        _service.value = null
    }

    fun startService() {
        val intent = Intent(context, PlaybackForegroundService::class.java)
        context.startForegroundService(intent)
    }
}
