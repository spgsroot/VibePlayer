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
    val smoothingAlpha: Float = 0.3f,
    val powerBoost: Float = 1.0f,  // 1.0 = 100%, 2.0 = 200% intensity
    val threshold: Float = 0.03f   // Shared silence cutoff for all haptic sources
)
