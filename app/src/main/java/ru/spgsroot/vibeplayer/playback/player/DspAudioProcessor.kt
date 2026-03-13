package ru.spgsroot.vibeplayer.playback.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAnalysisMs >= 33) {
            val analysisBuffer = inputBuffer.asReadOnlyBuffer().apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }
            val shortBuffer = analysisBuffer.asShortBuffer()

            val sampleCount = minOf(shortBuffer.remaining(), tempSamples.size)
            shortBuffer.get(tempSamples, 0, sampleCount)

            val amplitude = audioAnalyzer.analyze(tempSamples.copyOf(sampleCount))
            hapticMapper.emitNonBlocking(amplitude)
            lastAnalysisMs = now
        }

        inputBuffer.position(inputBuffer.position() + remaining)
    }

    override fun onReset() {
        hapticMapper.reset()
    }
}