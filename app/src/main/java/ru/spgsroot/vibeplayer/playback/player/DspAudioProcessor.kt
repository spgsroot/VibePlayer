package ru.spgsroot.vibeplayer.playback.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import ru.spgsroot.vibeplayer.domain.dsp.AudioAnalyzer
import ru.spgsroot.vibeplayer.domain.dsp.HapticMapper
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class DspAudioProcessor(
    private val audioAnalyzer: AudioAnalyzer,
    private val hapticMapper: HapticMapper
) : BaseAudioProcessor() {

    private var lastAnalysisMs = 0L
    private var lastLogMs = 0L
    private var tempSamples = ShortArray(2048)


    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Требуем 16-битный PCM для нашего анализатора (RMS алгоритм)
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Возвращаем тот же формат, так как мы не меняем звук
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer.duplicate())
        outputBuffer.flip()

        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysisMs >= 33) {
            val analysisBuffer = inputBuffer.asReadOnlyBuffer().apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            val shortBuffer = analysisBuffer.asShortBuffer()

            val sampleCount = minOf(shortBuffer.remaining(), tempSamples.size)
            shortBuffer.get(tempSamples, 0, sampleCount)

            val samples = if (sampleCount == tempSamples.size) {
                tempSamples
            } else {
                tempSamples.copyOf(sampleCount)
            }
            val rawAmplitude = audioAnalyzer.analyze(samples)
            val mappedAmplitude = mapPlayerAmplitude(rawAmplitude)
            logAmplitude(rawAmplitude, mappedAmplitude, now)
            hapticMapper.emitNonBlocking(mappedAmplitude)
            lastAnalysisMs = now
        }

        inputBuffer.position(inputBuffer.position() + remaining)
    }

    override fun onReset() {
        hapticMapper.reset()
    }

    private fun mapPlayerAmplitude(rawAmplitude: Float): Float {
        if (!rawAmplitude.isFinite()) return 0f
        return ((rawAmplitude - PLAYER_NOISE_FLOOR).coerceAtLeast(0f) * PLAYER_GAIN)
            .coerceIn(0f, 1f)
    }

    private fun logAmplitude(rawAmplitude: Float, mappedAmplitude: Float, now: Long) {
        if (now - lastLogMs >= LOG_INTERVAL_MS) {
            Log.d(TAG, "Player DSP amplitude raw=$rawAmplitude mapped=$mappedAmplitude")
            lastLogMs = now
        }
    }

    companion object {
        private const val TAG = "DspAudioProcessor"
        private const val PLAYER_NOISE_FLOOR = 0.004f
        private const val PLAYER_GAIN = 12f
        private const val LOG_INTERVAL_MS = 1_000L
    }
}
