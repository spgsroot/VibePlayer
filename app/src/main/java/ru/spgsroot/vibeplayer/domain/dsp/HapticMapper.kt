package ru.spgsroot.vibeplayer.domain.dsp

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticMapper @Inject constructor() {

    private val _intensity = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val intensity: SharedFlow<Float> = _intensity.asSharedFlow()

    private var emaValue = 0f
    private val alpha = 0.3f

    fun emitNonBlocking(rawAmplitude: Float) {
        emaValue = alpha * rawAmplitude + (1 - alpha) * emaValue
        val clamped = emaValue.coerceIn(0f, 1f)
        _intensity.tryEmit(clamped)
    }

    fun reset() {
        emaValue = 0f
    }
}
