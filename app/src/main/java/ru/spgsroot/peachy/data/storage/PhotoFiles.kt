package ru.spgsroot.peachy.data.storage

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.Locale

/** Filesystem-only part of photo storage, also usable without an Android runtime. */
internal class PhotoFiles(private val filesDir: File) {
    fun save(
        input: InputStream,
        fileName: String,
        totalBytes: Long = -1L,
        maxBytes: Long = PhotoStorageManager.DEFAULT_MAX_PHOTO_BYTES
    ): File = saveIn(input, "photos", extension(fileName), totalBytes, maxBytes)

    fun saveThumbnail(input: InputStream, photoId: Long): String =
        saveIn(input, "photo_thumbnails", "jpg", -1L, PhotoStorageManager.DEFAULT_MAX_PHOTO_BYTES,
            "photo_${photoId}_").absolutePath

    private fun saveIn(
        input: InputStream,
        directory: String,
        extension: String,
        totalBytes: Long,
        maxBytes: Long,
        prefix: String = "photo_"
    ): File {
        var target: File? = null
        try {
            input.use { source ->
                require(maxBytes > 0) { "maxBytes must be positive" }
                val limit = minOf(maxBytes, PhotoStorageManager.DEFAULT_MAX_PHOTO_BYTES)
                require(totalBytes <= limit) { "Photo is too large: $totalBytes bytes" }
                val root = File(filesDir, directory)
                check(root.isDirectory || root.mkdirs()) { "Cannot create photo storage" }
                // Display names are never storage paths; createTempFile reserves a unique name atomically.
                val file = File.createTempFile(prefix, ".$extension", root)
                target = file
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Photo copy cancelled")
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        require(count.toLong() <= limit - copied) { "Photo is too large" }
                        output.write(buffer, 0, count)
                        copied += count
                    }
                }
                return file
            }
        } catch (failure: Throwable) {
            target?.let { file ->
                if (file.exists() && !file.delete()) {
                    failure.addSuppressed(IOException("Cannot remove incomplete photo: ${file.name}"))
                }
            }
            throw failure
        }
    }

    fun deleteFile(path: String): Boolean = deleteIn("photos", path)
    fun deleteThumbnail(path: String): Boolean = deleteIn("photo_thumbnails", path)

    private fun deleteIn(directory: String, path: String): Boolean {
        if (path.isBlank()) return false
        return runCatching {
            val root = File(filesDir, directory).canonicalFile
            val target = File(path).canonicalFile
            // Same canonical containment rule as VideoFileDeletion, but an absent owned file is
            // already deleted. This lets a failed multi-file deletion be retried without losing its row.
            if (!target.path.startsWith(root.path + File.separator)) return false
            if (!target.exists()) return true
            target.isFile && target.delete()
        }.getOrDefault(false)
    }

    private fun extension(name: String): String = name.substringAfterLast('.', "")
        .lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "img"
}
