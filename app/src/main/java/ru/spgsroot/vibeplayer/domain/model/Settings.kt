package ru.spgsroot.vibeplayer.domain.model

data class Settings(
    val timerMs: Long,
    val playbackSpeed: Float,
    val dspConfig: DspConfig,
    val autoLockTimeoutMs: Long,
    val languageCode: String = "system"
)

data class DspConfig(
    val lowFreqHz: Int = 20,
    val highFreqHz: Int = 200,
    val smoothingAlpha: Float = 0.3f
)
