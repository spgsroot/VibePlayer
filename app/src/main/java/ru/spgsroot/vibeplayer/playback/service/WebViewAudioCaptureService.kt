package ru.spgsroot.vibeplayer.playback.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.MainActivity
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.domain.dsp.AudioAnalyzer
import ru.spgsroot.vibeplayer.domain.dsp.HapticInputRouter
import ru.spgsroot.vibeplayer.domain.dsp.HapticSource
import javax.inject.Inject

@AndroidEntryPoint
class WebViewAudioCaptureService : Service() {

    @Inject
    lateinit var audioAnalyzer: AudioAnalyzer

    @Inject
    lateinit var hapticInputRouter: HapticInputRouter

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var activeAudioRecord: AudioRecord? = null
    private var activeMediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private val resourceLock = Any()
    private var lastReadLogMs = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_CAPTURE -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    foregroundServiceType()
                )
                startCapture(intent)
            }
            else -> {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startCapture(intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "AudioPlaybackCapture requires Android 10+")
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.mediaProjectionData()
        if (resultCode == 0 || data == null) {
            Log.w(TAG, "Missing MediaProjection result data")
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        stopCapture()
        if (!hapticInputRouter.acquire(HapticSource.WEBVIEW)) {
            Log.w(TAG, "Unable to acquire WEBVIEW haptic source")
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val mediaProjectionManager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            Log.w(TAG, "MediaProjectionManager returned null projection")
            hapticInputRouter.release(HapticSource.WEBVIEW)
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        mediaProjection.registerCallback(callback, Handler(Looper.getMainLooper()))
        activeMediaProjection = mediaProjection
        mediaProjectionCallback = callback
        Log.d(
            TAG,
            "Starting WebView AudioPlaybackCapture for media/game/unknown usages without UID filter " +
                "(WebView renderers can run under isolated com.*.webview UIDs)"
        )

        captureJob = serviceScope.launch {
            runCaptureLoop(mediaProjection)
        }.also { job ->
            job.invokeOnCompletion { cause ->
                if (cause !is CancellationException) {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private suspend fun runCaptureLoop(mediaProjection: MediaProjection) {
        val audioRecord = runCatching { buildAudioRecord(mediaProjection) }.getOrElse {
            Log.e(TAG, "Unable to build AudioRecord", it)
            releaseCaptureResources()
            hapticInputRouter.release(HapticSource.WEBVIEW)
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord is not initialized")
            audioRecord.release()
            releaseCaptureResources()
            hapticInputRouter.release(HapticSource.WEBVIEW)
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val samples = ShortArray(bufferSizeBytes() / BYTES_PER_SAMPLE)

        try {
            audioRecord.startRecording()
            activeAudioRecord = audioRecord
            Log.d(TAG, "AudioRecord capture loop started")
            while (currentCoroutineContext().isActive) {
                val read = audioRecord.read(samples, 0, samples.size)
                if (read > 0) {
                    val analysisSamples = if (read == samples.size) samples else samples.copyOf(read)
                    val amplitude = audioAnalyzer.analyze(analysisSamples, SAMPLE_RATE)
                    val emitted = emitWebViewAmplitude(amplitude)
                    logReadProgress(read, amplitude, emitted)
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord read failed: $read")
                    break
                } else {
                    logReadProgress(read, 0f, emitted = false)
                }
            }
        } finally {
            Log.d(TAG, "AudioRecord capture loop stopped")
            releaseCaptureResources()
            hapticInputRouter.release(HapticSource.WEBVIEW)
        }
    }

    private fun emitWebViewAmplitude(amplitude: Float): Boolean {
        if (hapticInputRouter.emit(HapticSource.WEBVIEW, amplitude)) {
            return true
        }

        // WebView capture can start while PLAYER is still active or PLAYER can briefly re-acquire
        // during pause transitions. Re-acquire WEBVIEW here so captured WebView audio is not read
        // successfully but dropped before it reaches HapticMapper/CommandSender.
        return hapticInputRouter.acquire(HapticSource.WEBVIEW) &&
            hapticInputRouter.emit(HapticSource.WEBVIEW, amplitude)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildAudioRecord(mediaProjection: MediaProjection): AudioRecord {
        val playbackCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            // Do not restrict capture to appContext.applicationInfo.uid here.
            // Android WebView audio is commonly produced by a sandboxed WebView renderer process
            // with an isolated UID (for example com.huawei.webview:sandboxed_process0), so
            // addMatchingUid(app uid) filters out the very audio we need for WebView haptics.
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(playbackCaptureConfig)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSizeBytes())
            .build()
    }

    private fun logReadProgress(read: Int, amplitude: Float, emitted: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReadLogMs >= LOG_INTERVAL_MS) {
            Log.d(TAG, "AudioPlaybackCapture read=$read amplitude=$amplitude emitted=$emitted")
            lastReadLogMs = now
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        releaseCaptureResources()
        hapticInputRouter.release(HapticSource.WEBVIEW)
    }

    private fun releaseCaptureResources() {
        val resources = synchronized(resourceLock) {
            val audioRecord = activeAudioRecord
            val mediaProjection = activeMediaProjection
            val callback = mediaProjectionCallback
            activeAudioRecord = null
            activeMediaProjection = null
            mediaProjectionCallback = null
            Triple(audioRecord, mediaProjection, callback)
        }

        resources.first?.let { record ->
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
            runCatching { record.release() }
        }

        val mediaProjection = resources.second
        val callback = resources.third
        if (mediaProjection != null && callback != null) {
            runCatching { mediaProjection.unregisterCallback(callback) }
        }
        runCatching { mediaProjection?.stop() }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WebView audio capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Captures WebView playback audio for haptic analysis"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WebViewAudioCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("WebView audio capture")
            .setContentText("Analyzing WebView playback for haptics")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .build()
    }

    private fun foregroundServiceType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
    } else {
        0
    }

    private fun bufferSizeBytes(): Int {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return if (minBuffer > 0) minBuffer.coerceAtLeast(DEFAULT_BUFFER_SIZE_BYTES) else DEFAULT_BUFFER_SIZE_BYTES
    }

    private fun Intent.mediaProjectionData(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(EXTRA_RESULT_DATA)
    }

    companion object {
        private const val CHANNEL_ID = "webview_audio_capture_channel"
        private const val TAG = "WebViewCaptureService"
        private const val NOTIFICATION_ID = 2
        private const val LOG_INTERVAL_MS = 1_000L
        private const val SAMPLE_RATE = 44_100
        private const val BYTES_PER_SAMPLE = 2
        private const val DEFAULT_BUFFER_SIZE_BYTES = 4096
        private const val ACTION_CAPTURE = "ru.spgsroot.vibeplayer.playback.service.action.WEBVIEW_CAPTURE"
        private const val ACTION_STOP = "ru.spgsroot.vibeplayer.playback.service.action.WEBVIEW_CAPTURE_STOP"
        private const val EXTRA_RESULT_CODE = "ru.spgsroot.vibeplayer.playback.service.extra.RESULT_CODE"
        private const val EXTRA_RESULT_DATA = "ru.spgsroot.vibeplayer.playback.service.extra.RESULT_DATA"

        fun captureIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, WebViewAudioCaptureService::class.java).apply {
                action = ACTION_CAPTURE
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, WebViewAudioCaptureService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
