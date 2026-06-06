package ru.spgsroot.vibeplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val timerMs: Long,
    val playbackSpeed: Float,
    val dspLowFreq: Int,
    val dspHighFreq: Int,
    val dspSmoothingAlpha: Float,
    val dspPowerBoost: Float = 1.0f,
    val autoLockTimeoutMs: Long,
    val languageCode: String = "system"
)
