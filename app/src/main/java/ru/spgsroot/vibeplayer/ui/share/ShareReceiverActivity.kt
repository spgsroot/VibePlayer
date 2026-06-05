package ru.spgsroot.vibeplayer.ui.share

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.vibeplayer.data.repository.VideoRepository
import ru.spgsroot.vibeplayer.data.storage.InternalStorageManager
import ru.spgsroot.vibeplayer.domain.model.Video
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var storageManager: InternalStorageManager

    @Inject
    lateinit var videoRepository: VideoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSendVideo(intent)
            Intent.ACTION_VIEW -> handleViewVideo(intent)
            else -> finish()
        }
    }

    private fun handleSendVideo(intent: Intent) {
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: run {
            finish()
            return
        }
        processVideo(uri)
    }

    private fun handleViewVideo(intent: Intent) {
        val uri = intent.data ?: run {
            finish()
            return
        }
        processVideo(uri)
    }

    private fun processVideo(uri: Uri) {
        setContent {
            MaterialTheme {
                Surface {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        lifecycleScope.launch {
            try {
                importSharedVideo(uri)
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
            }
        }
    }

    private suspend fun importSharedVideo(uri: Uri) = withContext(Dispatchers.IO) {
        val fileName = "${System.currentTimeMillis()}.mp4"
        var savedFile: File? = null

        try {
            val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
            val file = storageManager.save(inputStream, fileName)
            savedFile = file

            val duration = extractDuration(file)
            val video = Video(
                id = 0,
                title = fileName,
                filePath = file.path,
                duration = duration,
                thumbnailPath = null,
                addedAt = System.currentTimeMillis(),
                fileSize = file.length(),
                isCorrupted = false
            )

            videoRepository.insert(video)
        } catch (e: Exception) {
            savedFile?.delete()
            throw e
        }
    }

    private fun extractDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } finally {
            retriever.release()
        }
    }
}
