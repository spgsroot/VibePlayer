package ru.spgsroot.peachy.data.storage

import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.downloader.ExternalDownloader
import ru.spgsroot.peachy.data.downloader.completeDownloadedVideo
import ru.spgsroot.peachy.data.repository.VideoRepository
import ru.spgsroot.peachy.domain.model.Video

/** Turns a downloaded or copied file into a library video. Shared by the player and the browser. */
@Singleton
class VideoImporter @Inject constructor(
    private val videoRepository: VideoRepository,
    private val externalDownloader: ExternalDownloader
) {
    /**
     * Downloads [url] and adds it to the library; a failed download or unreadable file leaves nothing behind.
     * Does its own network work on the IO dispatcher, so callers on the main thread are safe.
     */
    suspend fun importUrl(
        url: String,
        headers: Map<String, String> = emptyMap(),
        title: String? = null
    ): Result<Unit> = importUrlWithCopy(url, headers, title).map { Unit }

    suspend fun importUrlWithCopy(
        url: String,
        headers: Map<String, String> = emptyMap(),
        title: String? = null,
        copy: ((File) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        externalDownloader
            .download(url = url, fileName = downloadFileName(url), headers = headers)
            .fold(
                onSuccess = { file -> completeDownloadedVideo(file, { persist(file, title) }, copy) },
                onFailure = { Result.failure(it) }
            )
    }

    /** Adds the already saved [savedFile] to the library, deleting it when it cannot be read. */
    suspend fun persist(savedFile: File, title: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = extractMetadata(savedFile)
            val video = Video(
                id = 0,
                title = title?.takeIf { it.isNotBlank() } ?: metadata.title,
                filePath = savedFile.absolutePath,
                duration = metadata.duration,
                thumbnailPath = null,
                addedAt = System.currentTimeMillis(),
                fileSize = savedFile.length(),
                isCorrupted = false
            )
            videoRepository.insert(video)
            Unit
        }.onFailure {
            savedFile.delete()
        }
    }

    private fun downloadFileName(url: String): String {
        val lastSegment = runCatching { Uri.parse(url).lastPathSegment.orEmpty() }
            .getOrDefault("")

        val extension = lastSegment
            .substringAfterLast('.', "mp4")
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            ?: "mp4"

        return "${System.currentTimeMillis()}.$extension"
    }

    private class Metadata(val title: String, val duration: Long)

    private fun extractMetadata(file: File): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            Metadata(title = file.nameWithoutExtension, duration = duration)
        } finally {
            retriever.release()
        }
    }
}
