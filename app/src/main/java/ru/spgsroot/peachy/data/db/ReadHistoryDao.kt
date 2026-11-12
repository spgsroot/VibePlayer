package ru.spgsroot.peachy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadHistoryDao {
    @Query("SELECT * FROM read_history ORDER BY readAt DESC")
    fun observeAll(): Flow<List<ReadHistoryEntity>>

    @Query("SELECT * FROM read_history WHERE galleryId = :galleryId")
    suspend fun getById(galleryId: Int): ReadHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadHistoryEntity)
}
