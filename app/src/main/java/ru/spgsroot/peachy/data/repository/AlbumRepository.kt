package ru.spgsroot.peachy.data.repository

import androidx.room.withTransaction
import ru.spgsroot.peachy.data.db.AlbumEntity
import ru.spgsroot.peachy.data.db.AppDatabase
import javax.inject.Inject

class AlbumRepository @Inject constructor(private val database: AppDatabase) {
    private val dao = database.albumDao()
    fun observe() = dao.observe()
    suspend fun create(name: String): Long {
        require(name.isNotBlank())
        return dao.insert(AlbumEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
    }
    suspend fun rename(id: Long, name: String) { require(name.isNotBlank()); dao.rename(id, name.trim()) }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun move(videos: List<Long>, photos: List<Long>, albumId: Long?) = database.withTransaction {
        require(albumId == null || dao.exists(albumId)) { "Album no longer exists" }
        if (videos.isNotEmpty()) dao.moveVideos(videos, albumId)
        if (photos.isNotEmpty()) dao.movePhotos(photos, albumId)
    }
}
