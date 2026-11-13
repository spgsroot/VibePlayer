package ru.spgsroot.peachy.compress

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.abs

/** Reads the facts about a video file that the planner and the output check need. */
object VideoProbe {
    const val HEVC_MIME = "video/hevc"
    const val AVC_MIME = "video/avc"

    fun read(file: File): SourceInfo? {
        if (!file.isFile) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var video: MediaFormat? = null
            var hasAudio = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (video == null && mime.startsWith("video/")) video = format
                if (mime.startsWith("audio/")) hasAudio = true
            }
            val format = video ?: return null
            var width = format.intOrZero(MediaFormat.KEY_WIDTH)
            var height = format.intOrZero(MediaFormat.KEY_HEIGHT)
            val rotation = format.intOrZero(MediaFormat.KEY_ROTATION)
            if (rotation == 90 || rotation == 270) {
                val swap = width
                width = height
                height = swap
            }
            val durationMs = durationMs(file, format)
            if (width <= 0 || height <= 0 || durationMs <= 0) return null
            val size = file.length()
            return SourceInfo(
                width = width,
                height = height,
                fps = frameRate(file, format, durationMs),
                bitrate = size * 8000L / durationMs,
                durationMs = durationMs,
                sizeBytes = size,
                hasAudio = hasAudio,
                isHevc = format.getString(MediaFormat.KEY_MIME) == HEVC_MIME
            )
        } catch (e: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }

    private val encoders by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder } }

    /** Asks the device's own encoders; a size a codec cannot reach is not offered to it. */
    val encoderSupport = EncoderSupport { codec, width, height, fps ->
        val mime = if (codec == VideoCodec.HEVC) HEVC_MIME else AVC_MIME
        encoders.any { info ->
            info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                runCatching {
                    val video = info.getCapabilitiesForType(mime).videoCapabilities
                    video.areSizeAndRateSupported(width, height, fps.toDouble()) ||
                        video.areSizeAndRateSupported(height, width, fps.toDouble())
                }.getOrDefault(false)
        }
    }

    private fun durationMs(file: File, format: MediaFormat): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val fromContainer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (fromContainer != null && fromContainer > 0) return fromContainer
        } catch (e: Exception) {
            // fall through to the track duration
        } finally {
            retriever.release()
        }
        return if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
    }

    private fun frameRate(file: File, format: MediaFormat, durationMs: Long): Float {
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            val declared = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE) }
                .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE).toInt() }.getOrDefault(0) }
            if (declared > 0) return declared.toFloat()
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
            if (frames != null && frames > 0) return frames * 1000f / durationMs
        } catch (e: Exception) {
            // unknown; the planner assumes a common rate
        } finally {
            retriever.release()
        }
        return 0f
    }

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
}

/** Why a finished encode was thrown away instead of replacing the original. */
enum class Rejection { UNREADABLE, DURATION, FRAME_SIZE, AUDIO_LOST, NOT_SMALLER }

object OutputCheck {
    /** Null when the result is safe to put in place of [original]. */
    fun verdict(original: SourceInfo, result: SourceInfo?, plan: CompressionPlan): Rejection? {
        if (result == null) return Rejection.UNREADABLE
        val tolerance = maxOf(1000L, original.durationMs / 50)
        if (abs(result.durationMs - original.durationMs) > tolerance) return Rejection.DURATION
        if (abs(result.height - plan.height) > 2 || abs(result.width - plan.width) > 2) return Rejection.FRAME_SIZE
        if (original.hasAudio && !plan.removeAudio && !result.hasAudio) return Rejection.AUDIO_LOST
        if (result.sizeBytes <= 0 || result.sizeBytes >= original.sizeBytes) return Rejection.NOT_SMALLER
        return null
    }
}
