package ru.spgsroot.vibeplayer.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.spgsroot.vibeplayer.data.db.SettingsDao
import ru.spgsroot.vibeplayer.data.db.SettingsEntity
import ru.spgsroot.vibeplayer.domain.model.DspConfig
import ru.spgsroot.vibeplayer.domain.model.Settings
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    fun getSettings(): Flow<Settings?> = settingsDao.getSettings().map { it?.toDomain() }

    suspend fun updateTimer(timerMs: Long) {
        val current = settingsDao.getSettings().firstOrNull()
        current?.let {
            settingsDao.update(it.copy(timerMs = timerMs))
        }
    }

    suspend fun updatePlaybackSpeed(speed: Float) {
        val current = settingsDao.getSettings().firstOrNull()
        current?.let {
            settingsDao.update(it.copy(playbackSpeed = speed))
        }
    }

    suspend fun updateDspConfig(config: DspConfig) {
        val current = settingsDao.getSettings().firstOrNull()
        current?.let {
            settingsDao.update(it.copy(
                dspLowFreq = config.lowFreqHz,
                dspHighFreq = config.highFreqHz,
                dspSmoothingAlpha = config.smoothingAlpha
            ))
        }
    }

    suspend fun updateDspLowFreq(lowFreq: Int) {
        val current = settingsDao.getSettings().firstOrNull()
        current?.let {
            settingsDao.update(it.copy(dspLowFreq = lowFreq))
        }
    }

    suspend fun updateDspHighFreq(highFreq: Int) {
        val current = settingsDao.getSettings().firstOrNull()
        current?.let {
            settingsDao.update(it.copy(dspHighFreq = highFreq))
        }
    }

    suspend fun updateDspSmoothing(smoothing: Float) {
        val current = settingsDao.getSettings().firstOrNull()
        current?.let {
            settingsDao.update(it.copy(dspSmoothingAlpha = smoothing))
        }
    }

    suspend fun updateLanguage(languageCode: String) {
        val current = settingsDao.getSettings().firstOrNull()
        current?.let {
            settingsDao.update(it.copy(languageCode = languageCode))
        }
    }

    suspend fun initializeDefaults() {
        val existing = settingsDao.getSettings().firstOrNull()
        if (existing == null) {
            settingsDao.insert(
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
    }

    private fun SettingsEntity.toDomain() = Settings(
        timerMs = timerMs,
        playbackSpeed = playbackSpeed,
        dspConfig = DspConfig(dspLowFreq, dspHighFreq, dspSmoothingAlpha),
        autoLockTimeoutMs = autoLockTimeoutMs,
        languageCode = languageCode
    )
}
