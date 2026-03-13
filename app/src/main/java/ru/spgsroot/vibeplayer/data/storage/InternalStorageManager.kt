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
        totalBytes: Long = -1L
    ): File {
        val file = File(videosDir, fileName)
        inputStream.use { input ->
            file.outputStream().use { output ->
                if (onProgress == null || totalBytes <= 0) {
                    input.copyTo(output)
                } else {
                    val buffer = ByteArray(8192)
                    var bytesCopied = 0L
                    var read: Int
                    var lastProgressUpdate = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val currentTime = System.currentTimeMillis()
                        // Обновляем прогресс не чаще раза в 500 мс, чтобы не спамить UI и нотификации
                        if (currentTime - lastProgressUpdate > 500) {
                            lastProgressUpdate = currentTime
                            val progress = ((bytesCopied * 100) / totalBytes).toInt()
                            onProgress(progress)
                        }
                    }
                    onProgress(100) // Гарантированно отдаём 100% в конце
                }
            }
        }
        return file
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
}
