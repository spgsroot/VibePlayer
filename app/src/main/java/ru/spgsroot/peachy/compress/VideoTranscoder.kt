package ru.spgsroot.peachy.compress

import android.content.Context
import android.media.MediaCodecInfo
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranscodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Re-encodes one file with Media3 Transformer, on the device's own codecs. */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class VideoTranscoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Writes the re-encoded [input] to [output] as MP4. [onProgress] gets 0f..1f.
     * Cancelling the caller stops the encode; [output] is then left for the caller to delete.
     */
    suspend fun transcode(input: File, output: File, plan: CompressionPlan, onProgress: (Float) -> Unit) {
        // Transformer wants one thread with a Looper for every call.
        withContext(Dispatchers.Main.immediate) {
            coroutineScope {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(if (plan.codec == VideoCodec.HEVC) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                        .setEncoderFactory(
                            DefaultEncoderFactory.Builder(context)
                                .setRequestedVideoEncoderSettings(
                                    VideoEncoderSettings.Builder()
                                        .setBitrate(plan.videoBitrate)
                                        .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                                        .build()
                                )
                                // A size or codec the device cannot do is an error, not a silent downgrade.
                                .setEnableFallback(false)
                                .build()
                        )
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        TranscodeException(ExportException.getErrorCodeName(exportException.errorCode), exportException)
                                    )
                                }
                            }
                        })
                        .build()

                    val effects = ArrayList<Effect>()
                    if (plan.scaled) effects += Presentation.createForHeight(plan.height)
                    if (plan.dropFrames) effects += FrameDropEffect.createDefaultFrameDropEffect(plan.fps)
                    val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(input)))
                        .setRemoveAudio(plan.removeAudio)
                        .setEffects(Effects(emptyList(), effects))
                        .build()

                    continuation.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post { transformer.cancel() }
                    }
                    try {
                        transformer.start(item, output.absolutePath)
                    } catch (e: Exception) {
                        continuation.resumeWithException(TranscodeException("START", e))
                        return@suspendCancellableCoroutine
                    }

                    launch {
                        val holder = ProgressHolder()
                        while (isActive && continuation.isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / 100f)
                            }
                            delay(PROGRESS_POLL_MS)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val PROGRESS_POLL_MS = 400L
    }
}
