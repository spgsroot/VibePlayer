package ru.spgsroot.vibeplayer.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.spgsroot.vibeplayer.data.db.SettingsDao
import ru.spgsroot.vibeplayer.data.db.SettingsEntity
import ru.spgsroot.vibeplayer.domain.model.DspConfig
import ru.spgsroot.vibeplayer.domain.model.Settings
import javax.inject.Inject

class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    fun getSettings(): Flow<Settings?> = settingsDao.getSettings().map { it?.toDomain() }

    suspend fun updateTimer(timerMs: Long) {
        settingsDao.updateTimer(timerMs)
    }

    suspend fun updatePlaybackSpeed(speed: Float) {
        settingsDao.updatePlaybackSpeed(speed)
    }

    suspend fun updateDspConfig(config: DspConfig) {
        settingsDao.updateDspConfig(
            lowFreq = config.lowFreqHz,
            highFreq = config.highFreqHz,
            smoothing = config.smoothingAlpha
        )
    }

    suspend fun updateDspLowFreq(lowFreq: Int) {
        settingsDao.updateDspLowFreq(lowFreq)
    }

    suspend fun updateDspHighFreq(highFreq: Int) {
        settingsDao.updateDspHighFreq(highFreq)
    }

    suspend fun updateDspSmoothing(smoothing: Float) {
        settingsDao.updateDspSmoothing(smoothing)
    }

    suspend fun updateLanguage(languageCode: String) {
        settingsDao.updateLanguage(languageCode)
    }

    suspend fun initializeDefaults() {
        settingsDao.insertIfAbsent(
            SettingsEntity(
                timerMs = 30000L,
                playbackSpeed = 1.0f,
                dspLowFreq = 20,
                dspHighFreq = 200,
                dspSmoothingAlpha = 0.3f,
                autoLockTimeoutMs = 30000L,
                languageCode = "system"
            )
        )
    }

    private fun SettingsEntity.toDomain() = Settings(
        timerMs = timerMs,
        playbackSpeed = playbackSpeed,
        dspConfig = DspConfig(dspLowFreq, dspHighFreq, dspSmoothingAlpha),
        autoLockTimeoutMs = autoLockTimeoutMs,
        languageCode = languageCode
    )
}
