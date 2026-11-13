package ru.spgsroot.peachy.compress

import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class VideoCodec { HEVC, H264 }

enum class AudioMode { KEEP, REMOVE }

/** How the video bitrate is chosen. */
sealed interface RateControl {
    /** Bits spent per output pixel per frame; the bitrate follows resolution and frame rate. */
    data class Quality(val bitsPerPixel: Float) : RateControl

    data class Bitrate(val kbps: Int) : RateControl
}

/**
 * What the user asked for. A null limit keeps the source value.
 * [maxShareOfSource] caps the new video bitrate at that share of the source bitrate, so a file
 * that is already lean is never re-encoded to something larger.
 */
data class CompressionSettings(
    val codec: VideoCodec,
    val rate: RateControl,
    val maxShareOfSource: Float?,
    val maxHeight: Int?,
    val maxFps: Int?,
    val audio: AudioMode
)

/** A named starting point. [lossy] presets are the ones that visibly lower quality. */
data class CompressionPreset(val id: String, val lossy: Boolean, val settings: CompressionSettings)

object CompressionPresets {
    const val VISUALLY_LOSSLESS = "lossless"
    const val COMPACT = "compact"
    const val SMALLER = "smaller"
    const val SMALLEST = "smallest"

    /** Same frame size and rate; the bitrate stays high enough that the difference is not visible. */
    val visuallyLossless = CompressionPreset(
        VISUALLY_LOSSLESS, lossy = false,
        settings = CompressionSettings(
            VideoCodec.HEVC, RateControl.Quality(0.10f), maxShareOfSource = 0.75f,
            maxHeight = null, maxFps = null, audio = AudioMode.KEEP
        )
    )

    /** Same frame size and rate, a lower bitrate: still clean on a phone screen. */
    val compact = CompressionPreset(
        COMPACT, lossy = false,
        settings = CompressionSettings(
            VideoCodec.HEVC, RateControl.Quality(0.07f), maxShareOfSource = 0.55f,
            maxHeight = null, maxFps = null, audio = AudioMode.KEEP
        )
    )

    val smaller = CompressionPreset(
        SMALLER, lossy = true,
        settings = CompressionSettings(
            VideoCodec.HEVC, RateControl.Quality(0.05f), maxShareOfSource = 0.35f,
            maxHeight = 1080, maxFps = null, audio = AudioMode.KEEP
        )
    )

    val smallest = CompressionPreset(
        SMALLEST, lossy = true,
        settings = CompressionSettings(
            VideoCodec.HEVC, RateControl.Quality(0.045f), maxShareOfSource = 0.22f,
            maxHeight = 720, maxFps = 30, audio = AudioMode.KEEP
        )
    )

    val all = listOf(visuallyLossless, compact, smaller, smallest)

    fun byId(id: String): CompressionPreset? = all.firstOrNull { it.id == id }
}

/** What is known about a file before it is touched. Width and height are as displayed. */
data class SourceInfo(
    val width: Int,
    val height: Int,
    val fps: Float,
    /** Total bitrate of the file in bits per second. */
    val bitrate: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val hasAudio: Boolean,
    val isHevc: Boolean
)

/** The concrete numbers an encode will use. */
data class CompressionPlan(
    val codec: VideoCodec,
    val width: Int,
    val height: Int,
    val scaled: Boolean,
    val fps: Float,
    val dropFrames: Boolean,
    val videoBitrate: Int,
    val removeAudio: Boolean,
    val estimatedBytes: Long
)

/** Asks the device whether it has an encoder for a codec at a frame size and rate. */
fun interface EncoderSupport {
    fun supports(codec: VideoCodec, width: Int, height: Int, fps: Float): Boolean

    companion object {
        val ALL = EncoderSupport { _, _, _, _ -> true }
        val NO_HEVC = EncoderSupport { codec, _, _, _ -> codec != VideoCodec.HEVC }
    }
}

object CompressionPlanner {
    /** Bitrate that H.264 needs for the quality HEVC reaches at 1.0. */
    const val H264_PENALTY = 1.4f
    const val MIN_VIDEO_BITRATE = 200_000
    const val AUDIO_BITRATE_GUESS = 128_000
    private const val FALLBACK_FPS = 30f

    /** A new file must come out at most this share of the old one to be worth the encode. */
    const val WORTH_SHARE = 0.97

    /**
     * Turns [settings] and a probed [source] into encode parameters.
     * Returns null when the source has no usable video track.
     * When the device cannot encode HEVC at the output size, [support] switches to H.264 at a higher bitrate.
     */
    fun plan(source: SourceInfo, settings: CompressionSettings, support: EncoderSupport): CompressionPlan? {
        if (source.width <= 0 || source.height <= 0 || source.durationMs <= 0) return null

        val limit = settings.maxHeight
        val scaled = limit != null && source.height > limit
        val height = if (scaled) even(limit!!) else source.height
        val width = if (scaled) even(source.width.toDouble() * limit!! / source.height) else source.width

        val sourceFps = if (source.fps > 0f) source.fps else FALLBACK_FPS
        val fpsLimit = settings.maxFps
        val dropFrames = fpsLimit != null && sourceFps > fpsLimit + 0.5f
        val fps = if (dropFrames) fpsLimit!!.toFloat() else sourceFps
        val codec = if (settings.codec == VideoCodec.HEVC && !support.supports(VideoCodec.HEVC, width, height, fps)) {
            VideoCodec.H264
        } else {
            settings.codec
        }

        var bitrate = when (val rate = settings.rate) {
            is RateControl.Quality -> {
                val base = rate.bitsPerPixel.toDouble() * width * height * fps
                if (codec == VideoCodec.H264) base * H264_PENALTY else base
            }
            is RateControl.Bitrate -> rate.kbps * 1000.0
        }
        val share = settings.maxShareOfSource
        if (share != null && source.bitrate > 0) {
            bitrate = minOf(bitrate, source.bitrate * share.toDouble())
        }
        val videoBitrate = bitrate.roundToLong().coerceIn(MIN_VIDEO_BITRATE.toLong(), Int.MAX_VALUE.toLong()).toInt()

        val removeAudio = settings.audio == AudioMode.REMOVE
        val audioBitrate = if (removeAudio || !source.hasAudio) 0 else AUDIO_BITRATE_GUESS
        val estimated = (videoBitrate + audioBitrate).toDouble() * source.durationMs / 8000.0

        return CompressionPlan(
            codec = codec, width = width, height = height, scaled = scaled,
            fps = fps, dropFrames = dropFrames, videoBitrate = videoBitrate,
            removeAudio = removeAudio, estimatedBytes = estimated.roundToLong()
        )
    }

    /** False when the encode would barely shrink the file, so it is skipped. */
    fun isWorthwhile(plan: CompressionPlan, source: SourceInfo): Boolean =
        source.sizeBytes > 0 && plan.estimatedBytes < source.sizeBytes * WORTH_SHARE

    private fun even(value: Double): Int = (value / 2).roundToInt() * 2

    private fun even(value: Int): Int = value / 2 * 2
}
