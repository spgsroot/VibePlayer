package ru.spgsroot.peachy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY addedAt DESC, id DESC")
    fun getAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY addedAt DESC, id DESC")
    suspend fun getAllNow(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("UPDATE photos SET title = :title, filePath = :filePath, thumbnailPath = :thumbnailPath, addedAt = :addedAt, fileSize = :fileSize, width = :width, height = :height WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, filePath: String, thumbnailPath: String?, addedAt: Long, fileSize: Long, width: Int, height: Int)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
