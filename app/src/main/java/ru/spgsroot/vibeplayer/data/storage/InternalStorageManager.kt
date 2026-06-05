package ru.spgsroot.vibeplayer.data.storage

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val videosDir: File
        get() = File(context.filesDir, "videos").apply { mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, "thumbnails").apply { mkdirs() }

    fun save(
        inputStream: InputStream,
        fileName: String,
        onProgress: ((Int) -> Unit)? = null,
        totalBytes: Long = -1L,
        maxBytes: Long = DEFAULT_MAX_VIDEO_BYTES
    ): File {
        require(maxBytes > 0) { "maxBytes must be positive" }
        if (totalBytes > maxBytes) {
            throw IllegalArgumentException("File is too large: $totalBytes bytes")
        }
        if (totalBytes > 0 && totalBytes + MIN_FREE_BYTES > getAvailableSpace()) {
            throw IllegalStateException("Not enough free space to save file")
        }

        val file = File(videosDir, File(fileName).name)
        return try {
            inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var bytesCopied = 0L
                    var read: Int
                    var lastProgressUpdate = 0L

                    while (input.read(buffer).also { read = it } >= 0) {
                        if (read == 0) continue

                        bytesCopied += read
                        if (bytesCopied > maxBytes) {
                            throw IllegalArgumentException("File is too large: $bytesCopied bytes")
                        }

                        output.write(buffer, 0, read)

                        if (onProgress != null && totalBytes > 0) {
                            val currentTime = System.currentTimeMillis()
                            // Обновляем прогресс не чаще раза в 500 мс, чтобы не спамить UI и нотификации
                            if (currentTime - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL_MS) {
                                lastProgressUpdate = currentTime
                                val progress = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100)
                                onProgress(progress)
                            }
                        }
                    }

                    if (onProgress != null && totalBytes > 0) {
                        onProgress(100) // Гарантированно отдаём 100% в конце
                    }
                }
            }
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    fun saveThumbnail(inputStream: InputStream, videoId: Long): String {
        val fileName = "$videoId.jpg"
        val file = File(thumbnailsDir, fileName)
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    fun delete(videoId: Long): Boolean {
        var deleted = false
        
        // Delete video file
        val videoFiles = videosDir.listFiles() ?: emptyArray()
        val videoFile = videoFiles.find { it.nameWithoutExtension == videoId.toString() }
        if (videoFile != null) {
            deleted = videoFile.delete()
        }
        
        // Delete thumbnail file
        val thumbnailFiles = thumbnailsDir.listFiles() ?: emptyArray()
        val thumbnailFile = thumbnailFiles.find { it.nameWithoutExtension == videoId.toString() }
        if (thumbnailFile != null) {
            deleted = thumbnailFile.delete() || deleted
        }
        
        return deleted
    }

    fun deleteThumbnail(thumbnailPath: String): Boolean {
        return try {
            val file = File(thumbnailPath)
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    fun getFile(videoId: Long): File? {
        val files = videosDir.listFiles() ?: return null
        return files.find { it.nameWithoutExtension == videoId.toString() }
    }

    fun getThumbnailFile(videoId: Long): File? {
        val files = thumbnailsDir.listFiles() ?: return null
        return files.find { it.nameWithoutExtension == videoId.toString() }
    }

    fun getTotalSize(): Long {
        val videoSize = videosDir.listFiles()?.sumOf { it.length() } ?: 0L
        val thumbnailSize = thumbnailsDir.listFiles()?.sumOf { it.length() } ?: 0L
        return videoSize + thumbnailSize
    }

    fun getAvailableSpace(): Long {
        val stat = StatFs(videosDir.path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun cleanup() {
        videosDir.listFiles()?.forEach { it.delete() }
        thumbnailsDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val DEFAULT_MAX_VIDEO_BYTES = 2L * 1024L * 1024L * 1024L
        private const val MIN_FREE_BYTES = 50L * 1024L * 1024L
        private const val COPY_BUFFER_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }
}
