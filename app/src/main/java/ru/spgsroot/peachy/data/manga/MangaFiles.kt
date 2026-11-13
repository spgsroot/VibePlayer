package ru.spgsroot.peachy.data.manga

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaFiles @Inject constructor(
    @ApplicationContext context: Context
) {
    private val root = File(context.filesDir, "manga")

    fun pageFile(galleryId: Int, remotePath: String): File? {
        val name = remotePath.trim().substringAfterLast('/')
        if (name.isBlank() || name.contains("..") || name.contains('/') || name.contains('\\')) return null
        return File(File(root, galleryId.toString()), name)
    }

    fun save(galleryId: Int, remotePath: String, bytes: ByteArray): File? {
        val file = pageFile(galleryId, remotePath) ?: return null
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    /** Every file saved for the gallery, for the vault export. */
    fun listFiles(galleryId: Int): List<File> =
        File(root, galleryId.toString()).listFiles()?.filter { it.isFile }.orEmpty()

    /** Writes one page from [input] unless the file is already there; null when it is, or [name] is not a plain file name. */
    fun restore(galleryId: Int, name: String, input: java.io.InputStream): File? {
        val file = pageFile(galleryId, name) ?: return null
        if (file.exists()) return null
        file.parentFile?.mkdirs()
        try {
            file.outputStream().use { input.copyTo(it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return file
    }

    fun deleteGallery(galleryId: Int) {
        File(root, galleryId.toString()).deleteRecursively()
    }
}
