package ru.spgsroot.peachy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryGalleryDao {
    @Query("SELECT * FROM library_galleries ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<LibraryGalleryEntity>>

    @Query("SELECT * FROM library_galleries")
    suspend fun getAll(): List<LibraryGalleryEntity>

    @Query("SELECT * FROM library_galleries WHERE id = :id")
    suspend fun getById(id: Int): LibraryGalleryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LibraryGalleryEntity)

    @Query("DELETE FROM library_galleries WHERE id = :id")
    suspend fun deleteById(id: Int)
}
