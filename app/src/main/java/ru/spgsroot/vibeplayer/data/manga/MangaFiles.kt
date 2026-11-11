package ru.spgsroot.vibeplayer.data.manga

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

    fun deleteGallery(galleryId: Int) {
        File(root, galleryId.toString()).deleteRecursively()
    }
}
