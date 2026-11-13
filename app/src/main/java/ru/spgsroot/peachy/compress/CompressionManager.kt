package ru.spgsroot.peachy.compress

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.repository.VideoRepository
import ru.spgsroot.peachy.domain.model.Video
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CompressionState {
    data object Idle : CompressionState

    data class Running(
        val total: Int,
        /** 1-based position of the video being encoded. */
        val index: Int,
        val title: String,
        /** 0f..1f for the current video. */
        val progress: Float,
        val savedBytes: Long
    ) : CompressionState

    data class Finished(
        val compressed: Int,
        val skipped: Int,
        val failed: Int,
        val savedBytes: Long,
        val cancelled: Boolean
    ) : CompressionState
}

/**
 * Runs compression jobs for the whole app, so leaving the gallery does not stop one.
 * An original is replaced only after the new file was read back and checked, and only when it is
 * smaller; on any failure or cancel the original stays exactly as it was.
 */
@Singleton
class CompressionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoRepository: VideoRepository,
    private val transcoder: VideoTranscoder
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<CompressionState>(CompressionState.Idle)
    val state: StateFlow<CompressionState> = _state.asStateFlow()

    init {
        // A killed process can leave a half-written encode behind.
        scope.launch(Dispatchers.IO) {
            File(context.filesDir, "videos").listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
        }
    }

    private enum class Outcome { COMPRESSED, SKIPPED, FAILED }

    fun start(videos: List<Video>, settings: CompressionSettings) {
        if (job?.isActive == true || videos.isEmpty()) return
        job = scope.launch {
            var compressed = 0
            var skipped = 0
            var failed = 0
            var saved = 0L
            var cancelled = false
            try {
                videos.forEachIndexed { i, video ->
                    _state.value = CompressionState.Running(videos.size, i + 1, video.title, 0f, saved)
                    val before = saved
                    val outcome = try {
                        one(video, settings,
                            onProgress = { p ->
                                _state.value = CompressionState.Running(videos.size, i + 1, video.title, p, before)
                            },
                            onSaved = { saved += it }
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Outcome.FAILED
                    }
                    when (outcome) {
                        Outcome.COMPRESSED -> compressed++
                        Outcome.SKIPPED -> skipped++
                        Outcome.FAILED -> failed++
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
            }
            _state.value = CompressionState.Finished(compressed, skipped, failed, saved, cancelled)
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Hides the result once the UI has shown it. */
    fun consumeFinished() {
        if (_state.value is CompressionState.Finished) _state.value = CompressionState.Idle
    }

    private suspend fun one(
        video: Video,
        settings: CompressionSettings,
        onProgress: (Float) -> Unit,
        onSaved: (Long) -> Unit
    ): Outcome {
        val file = File(video.filePath)
        val source = withContext(Dispatchers.IO) { VideoProbe.read(file) } ?: return Outcome.FAILED
        val plan = withContext(Dispatchers.Default) { CompressionPlanner.plan(source, settings, VideoProbe.encoderSupport) }
            ?: return Outcome.FAILED
        if (!CompressionPlanner.isWorthwhile(plan, source)) return Outcome.SKIPPED

        val dir = file.parentFile ?: return Outcome.FAILED
        val needed = (plan.estimatedBytes * SPACE_MARGIN).toLong() + MIN_FREE_BYTES
        if (StatFs(dir.path).availableBytes < needed) return Outcome.FAILED

        val temp = File(dir, "${video.id}$PART_SUFFIX")
        try {
            transcoder.transcode(file, temp, plan, onProgress)
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: Exception) {
            temp.delete()
            return Outcome.FAILED
        }

        return withContext(Dispatchers.IO + NonCancellable) {
            val result = VideoProbe.read(temp)
            val rejection = OutputCheck.verdict(source, result, plan)
            if (rejection != null || result == null) {
                temp.delete()
                return@withContext if (rejection == Rejection.NOT_SMALLER) Outcome.SKIPPED else Outcome.FAILED
            }
            if (replace(video, file, temp, result)) {
                onSaved(source.sizeBytes - result.sizeBytes)
                Outcome.COMPRESSED
            } else {
                Outcome.FAILED
            }
        }
    }

    /** Puts [temp] where [original] was, then points the library at it. */
    private suspend fun replace(video: Video, original: File, temp: File, result: SourceInfo): Boolean {
        // The video may have been deleted or moved while it was encoding.
        val current = videoRepository.getById(video.id)
        if (current == null || current.filePath != video.filePath || !original.isFile) {
            temp.delete()
            return false
        }
        val dir = original.parentFile ?: return false
        val target = if (original.extension.equals("mp4", ignoreCase = true)) {
            original
        } else {
            File(dir, "${original.nameWithoutExtension}-${video.id}.mp4")
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            temp.delete()
            return false
        }
        return try {
            videoRepository.update(
                current.copy(filePath = target.absolutePath, fileSize = result.sizeBytes, duration = result.durationMs, isCorrupted = false)
            )
            if (target != original) original.delete()
            true
        } catch (e: Exception) {
            // The old file is still the one the library knows about.
            if (target != original) target.delete()
            false
        }
    }

    private companion object {
        const val PART_SUFFIX = ".compress.part"
        const val SPACE_MARGIN = 1.15
        const val MIN_FREE_BYTES = 64L * 1024 * 1024
    }
}
