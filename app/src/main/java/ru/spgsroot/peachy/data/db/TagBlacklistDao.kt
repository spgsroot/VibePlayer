package ru.spgsroot.peachy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TagBlacklistDao {
    @Query("SELECT * FROM tag_blacklist ORDER BY kind, value")
    suspend fun getAll(): List<TagBlacklistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TagBlacklistEntity)

    @Query("DELETE FROM tag_blacklist WHERE kind = :kind AND value = :value")
    suspend fun delete(kind: String, value: String)
}
