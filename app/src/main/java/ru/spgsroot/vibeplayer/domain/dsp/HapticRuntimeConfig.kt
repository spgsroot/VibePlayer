package ru.spgsroot.vibeplayer.domain.dsp

import ru.spgsroot.vibeplayer.domain.model.DspConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticRuntimeConfig @Inject constructor() {
    @Volatile private var snapshot = Snapshot()

    val smoothingAlpha: Float get() = snapshot.smoothingAlpha
    val powerBoost: Float get() = snapshot.powerBoost
    val threshold: Float get() = snapshot.threshold

    fun update(config: DspConfig) {
        snapshot = Snapshot(
            smoothingAlpha = config.smoothingAlpha.coerceIn(MIN_SMOOTHING_ALPHA, MAX_SMOOTHING_ALPHA),
            powerBoost = config.powerBoost.coerceIn(MIN_POWER_BOOST, MAX_POWER_BOOST),
            threshold = config.threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        )
    }

    private data class Snapshot(
        val smoothingAlpha: Float = DEFAULT_SMOOTHING_ALPHA,
        val powerBoost: Float = DEFAULT_POWER_BOOST,
        val threshold: Float = DEFAULT_THRESHOLD
    )

    companion object {
        const val DEFAULT_SMOOTHING_ALPHA = 0.3f
        const val DEFAULT_POWER_BOOST = 1.0f
        const val DEFAULT_THRESHOLD = 0.03f

        const val MIN_SMOOTHING_ALPHA = 0.1f
        const val MAX_SMOOTHING_ALPHA = 0.5f
        const val MIN_POWER_BOOST = 0.5f
        const val MAX_POWER_BOOST = 3.0f
        const val MIN_THRESHOLD = 0.0f
        const val MAX_THRESHOLD = 0.15f
    }
}
