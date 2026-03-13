package ru.spgsroot.vibeplayer.data.db

import androidx.room.*

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun getState(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: PlaybackStateEntity)

    @Query("DELETE FROM playback_state WHERE id = 1")
    suspend fun clear()
}
