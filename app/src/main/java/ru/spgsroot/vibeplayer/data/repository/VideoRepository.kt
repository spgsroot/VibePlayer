package ru.spgsroot.vibeplayer.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.spgsroot.vibeplayer.data.db.VideoDao
import ru.spgsroot.vibeplayer.data.db.VideoEntity
import ru.spgsroot.vibeplayer.domain.model.Video
import javax.inject.Inject

class VideoRepository @Inject constructor(
    private val videoDao: VideoDao
) {
    fun getAll(): Flow<List<Video>> = videoDao.getAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getById(id: Long): Video? = videoDao.getById(id)?.toDomain()

    suspend fun insert(video: Video): Long = videoDao.insert(video.toEntity())

    suspend fun update(video: Video) = videoDao.update(video.toEntity())

    suspend fun delete(video: Video) = videoDao.delete(video.toEntity())

    suspend fun deleteById(id: Long) = videoDao.deleteById(id)

    suspend fun markAsCorrupted(id: Long) = videoDao.markAsCorrupted(id)

    private fun VideoEntity.toDomain() = Video(
        id = id,
        title = title,
        filePath = filePath,
        duration = duration,
        thumbnailPath = thumbnailPath,
        addedAt = addedAt,
        fileSize = fileSize,
        isCorrupted = isCorrupted
    )

    private fun Video.toEntity() = VideoEntity(
        id = id,
        title = title,
        filePath = filePath,
        duration = duration,
        thumbnailPath = thumbnailPath,
        addedAt = addedAt,
        fileSize = fileSize,
        isCorrupted = isCorrupted
    )
}
