package ru.spgsroot.peachy.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "albums")
data class AlbumEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long)

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE, id")
    fun observe(): Flow<List<AlbumEntity>>
    @Query("SELECT * FROM albums")
    suspend fun getAllNow(): List<AlbumEntity>
    @Insert
    suspend fun insert(album: AlbumEntity): Long
    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT EXISTS(SELECT 1 FROM albums WHERE id = :id)")
    suspend fun exists(id: Long): Boolean
    @Query("UPDATE videos SET albumId = :albumId WHERE id IN (:ids)")
    suspend fun moveVideos(ids: List<Long>, albumId: Long?)
    @Query("UPDATE photos SET albumId = :albumId WHERE id IN (:ids)")
    suspend fun movePhotos(ids: List<Long>, albumId: Long?)
}
