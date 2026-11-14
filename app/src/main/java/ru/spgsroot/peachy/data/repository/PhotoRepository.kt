package ru.spgsroot.peachy.data.repository

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.db.AppDatabase
import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.data.storage.PhotoStorageManager

class PhotoRepository @Inject constructor(
    private val database: AppDatabase,
    private val storage: PhotoStorageManager
) {
    fun getAll(): Flow<List<PhotoEntity>> = database.photoDao().getAll()

    suspend fun update(photo: PhotoEntity) = database.photoDao().updateContent(
        photo.id, photo.title, photo.filePath, photo.thumbnailPath, photo.addedAt,
        photo.fileSize, photo.width, photo.height
    )

    /** Keep the row if either deletion fails, so a user can retry rather than orphan private bytes. */
    suspend fun delete(photo: PhotoEntity): Unit = withContext(Dispatchers.IO) {
        val current = database.photoDao().getById(photo.id) ?: return@withContext
        current.thumbnailPath?.let {
            if (!storage.deleteThumbnail(it)) throw IOException("Cannot delete photo thumbnail")
        }
        if (!storage.deleteFile(current.filePath)) throw IOException("Cannot delete photo file")
        database.photoDao().deleteById(current.id)
    }
}
