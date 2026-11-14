package ru.spgsroot.peachy.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStorageManager @Inject constructor(@ApplicationContext context: Context) {
    private val files = PhotoFiles(context.filesDir)

    fun save(
        inputStream: InputStream,
        fileName: String,
        totalBytes: Long = -1L,
        maxBytes: Long = DEFAULT_MAX_PHOTO_BYTES
    ): File = files.save(inputStream, fileName, totalBytes, maxBytes)

    fun saveThumbnail(inputStream: InputStream, photoId: Long): String = files.saveThumbnail(inputStream, photoId)

    fun deleteFile(filePath: String): Boolean = files.deleteFile(filePath)
    fun deleteThumbnail(thumbnailPath: String): Boolean = files.deleteThumbnail(thumbnailPath)

    companion object {
        const val DEFAULT_MAX_PHOTO_BYTES = 100L * 1024L * 1024L
    }
}
