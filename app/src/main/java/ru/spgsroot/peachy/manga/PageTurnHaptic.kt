package ru.spgsroot.peachy.manga

import io.github.spgsroot.buttplug.device.ActuatorType
import ru.spgsroot.peachy.device.buttplug.isAudioDrivable

object PageTurnHaptic {
    const val DEFAULT_INTENSITY = 0.35f
    const val DEFAULT_DURATION_MS = 180L

    fun steps(intensity: Float, durationMs: Long): List<Float> {
        val peak = intensity.coerceIn(0f, 1f)
        if (durationMs <= 0L || peak == 0f) return listOf(0f, 0f, 0f)
        return listOf(0f, peak, 0f)
    }

    fun drives(type: ActuatorType): Boolean = type.isAudioDrivable()
}
