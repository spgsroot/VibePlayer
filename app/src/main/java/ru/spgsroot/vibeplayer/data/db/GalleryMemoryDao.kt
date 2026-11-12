package ru.spgsroot.vibeplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryMemoryDao {
    @Query("SELECT * FROM gallery_memory")
    fun observeAll(): Flow<List<GalleryMemoryEntity>>

    @Query("SELECT * FROM gallery_memory WHERE galleryId = :galleryId")
    suspend fun getById(galleryId: Int): GalleryMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GalleryMemoryEntity)
}
