package ru.spgsroot.vibeplayer.domain.dsp

import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioAnalyzer @Inject constructor() {

    fun analyze(pcmBuffer: ShortArray, sampleRate: Int = 44100): Float {
        if (pcmBuffer.isEmpty()) return 0f

        // Calculate RMS amplitude
        var sum = 0.0
        for (sample in pcmBuffer) {
            val normalized = sample / 32768.0
            sum += normalized * normalized
        }
        val rms = sqrt(sum / pcmBuffer.size)

        return rms.toFloat()
    }
}
