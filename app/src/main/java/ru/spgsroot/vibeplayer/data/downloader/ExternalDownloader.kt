package ru.spgsroot.vibeplayer.data.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.data.storage.InternalStorageManager
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: InternalStorageManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "download_channel"

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Загрузки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о скачивании видео"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress

    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit = {}
    ): Result<File> {
        val notificationId = url.hashCode()

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notification_download_title))
            .setContentText(context.getString(R.string.notification_connecting))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, true)

        notificationManager.notify(notificationId, builder.build())
        _downloadProgress.value = DownloadProgress.Downloading(0)
        onProgress(0)

        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = "HTTP ${response.code}: ${context.getString(R.string.error_download_failed)}"

                    builder.setContentTitle(context.getString(R.string.notification_download_error))
                        .setContentText(message)
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setSmallIcon(android.R.drawable.stat_notify_error)

                    notificationManager.notify(notificationId, builder.build())
                    _downloadProgress.value = DownloadProgress.Error(message)
                    return Result.failure(IllegalStateException(message))
                }

                val body = response.body ?: run {
                    val message = context.getString(R.string.error_empty_response)

                    builder.setContentTitle(context.getString(R.string.notification_download_error))
                        .setContentText(message)
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setSmallIcon(android.R.drawable.stat_notify_error)

                    notificationManager.notify(notificationId, builder.build())
                    _downloadProgress.value = DownloadProgress.Error(message)
                    return Result.failure(IllegalStateException(message))
                }

                val totalBytes = body.contentLength()

                val file = body.byteStream().use { inputStream ->
                    storageManager.save(
                        inputStream = inputStream,
                        fileName = fileName,
                        totalBytes = totalBytes,
                        onProgress = { progress ->
                            builder.setProgress(100, progress, false)
                                .setContentText(context.getString(R.string.notification_downloaded, progress))
                            notificationManager.notify(notificationId, builder.build())
                            _downloadProgress.value = DownloadProgress.Downloading(progress)
                            onProgress(progress)
                        }
                    )
                }

                builder.setContentTitle(context.getString(R.string.notification_download_complete))
                    .setContentText(context.getString(R.string.notification_saved))
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)

                notificationManager.notify(notificationId, builder.build())
                _downloadProgress.value = DownloadProgress.Success(file)
                onProgress(100)

                Result.success(file)
            }
        } catch (e: Exception) {
            builder.setContentTitle("Ошибка скачивания")
                .setContentText(e.message ?: "Неизвестная ошибка")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setSmallIcon(android.R.drawable.stat_notify_error)

            notificationManager.notify(notificationId, builder.build())
            _downloadProgress.value = DownloadProgress.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

sealed class DownloadProgress {
    data object Idle : DownloadProgress()
    data class Downloading(val progress: Int) : DownloadProgress()
    data class Success(val file: File) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
