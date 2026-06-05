package ru.spgsroot.vibeplayer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: SettingsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(settings: SettingsEntity)

    @Update
    suspend fun update(settings: SettingsEntity)

    @Query("UPDATE settings SET timerMs = :timerMs WHERE id = 1")
    suspend fun updateTimer(timerMs: Long)

    @Query("UPDATE settings SET playbackSpeed = :speed WHERE id = 1")
    suspend fun updatePlaybackSpeed(speed: Float)

    @Query("""
        UPDATE settings
        SET dspLowFreq = :lowFreq,
            dspHighFreq = :highFreq,
            dspSmoothingAlpha = :smoothing
        WHERE id = 1
    """)
    suspend fun updateDspConfig(lowFreq: Int, highFreq: Int, smoothing: Float)

    @Query("UPDATE settings SET dspLowFreq = :lowFreq WHERE id = 1")
    suspend fun updateDspLowFreq(lowFreq: Int)

    @Query("UPDATE settings SET dspHighFreq = :highFreq WHERE id = 1")
    suspend fun updateDspHighFreq(highFreq: Int)

    @Query("UPDATE settings SET dspSmoothingAlpha = :smoothing WHERE id = 1")
    suspend fun updateDspSmoothing(smoothing: Float)

    @Query("UPDATE settings SET languageCode = :languageCode WHERE id = 1")
    suspend fun updateLanguage(languageCode: String)
}
