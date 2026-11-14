package ru.spgsroot.peachy.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.domain.model.Video
import java.io.File

/** Width over height of the tall cover used by [GalleryLayout.Large]; the manga catalog uses the same. */
private const val PORTRAIT_ASPECT = 0.7f

/** What a card can ask the gallery to do with its video. */
private class CardActions(
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val onRename: () -> Unit,
    val onChangeThumbnail: () -> Unit,
    val hasNote: Boolean,
    val onNote: () -> Unit,
    val onCompress: () -> Unit,
    val onMove: () -> Unit,
    val onDelete: () -> Unit
)

@OptIn(ExperimentalCoilApi::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryItemCard(
    video: Video,
    layout: GalleryLayout,
    isSelected: Boolean,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameClick: () -> Unit,
    onChangeThumbnailClick: () -> Unit,
    hasNote: Boolean,
    onNoteClick: () -> Unit,
    onCompressClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val imageData by produceState(
        initialValue = video.filePath,
        video.thumbnailPath,
        video.filePath
    ) {
        value = withContext(Dispatchers.IO) {
            video.thumbnailPath?.takeIf { File(it).exists() } ?: video.filePath
        }
    }
    val actions = CardActions(
        onClick = onVideoClick,
        onLongClick = onLongClick,
        onRename = onRenameClick,
        onChangeThumbnail = onChangeThumbnailClick,
        hasNote = hasNote,
        onNote = onNoteClick,
        onCompress = onCompressClick,
        onMove = onMoveClick,
        onDelete = onDeleteClick
    )
    when (layout) {
        GalleryLayout.Two, GalleryLayout.One -> WideCard(video, imageData, isSelected, actions)
        GalleryLayout.Large -> PortraitCard(video, imageData, isSelected, actions)
        GalleryLayout.List -> RowCard(video, imageData, isSelected, actions)
    }
}

/** Compact tiles keep metadata outside the thumbnail so neither hides the other. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WideCard(video: Video, imageData: Any, isSelected: Boolean, actions: CardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .combinedClickable(onClick = actions.onClick, onLongClick = actions.onLongClick),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
        ) {
            Box {
                Preview(video, imageData, Modifier.fillMaxSize())
                if (isSelected) SelectedMark()
                else VideoMenu(actions, Color.White, Modifier.align(Alignment.TopEnd))
            }
        }
        Text(video.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(videoDetails(video), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A tall cover with the title and length underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitCard(video: Video, imageData: Any, isSelected: Boolean, actions: CardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PORTRAIT_ASPECT)
                .combinedClickable(onClick = actions.onClick, onLongClick = actions.onLongClick),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box {
                Preview(video, imageData, Modifier.fillMaxSize())
                if (isSelected) SelectedMark()
                if (!isSelected) {
                    VideoMenu(actions, Color.White, Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = videoDetails(video),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One row: a small preview, the title and length, and the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowCard(video: Video, imageData: Any, isSelected: Boolean, actions: CardActions) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = actions.onClick, onLongClick = actions.onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Preview(
                video, imageData,
                Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = videoDetails(video),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            } else {
                VideoMenu(actions, MaterialTheme.colorScheme.onSurfaceVariant, Modifier)
            }
        }
    }
}

/** The first seconds of the video, or the cover picked for it. */
@OptIn(ExperimentalCoilApi::class)
@Composable
private fun Preview(video: Video, imageData: Any, modifier: Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageData)
            .decoderFactory(VideoFrameDecoder.Factory()) // Обязательно для извлечения кадра
            .videoFrameMicros(3000000) // Берем кадр с 3-й секунды (3 000 000 микросекунд)
            .crossfade(true)
            .build(),
        contentDescription = video.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** Dims the preview and shows a ticked box. */
@Composable
private fun BoxScope.SelectedMark() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
    ) {
        Checkbox(
            checked = true,
            onCheckedChange = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        )
    }
}

/** The three-dot button and the menu it opens. */
@Composable
private fun VideoMenu(actions: CardActions, tint: Color, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.content_description_more),
                tint = tint
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_rename)) },
                onClick = {
                    open = false
                    actions.onRename()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_change_cover)) },
                onClick = {
                    open = false
                    actions.onChangeThumbnail()
                },
                leadingIcon = { Icon(Icons.Default.Image, null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (actions.hasNote) R.string.menu_note_edit else R.string.menu_note_add)) },
                onClick = {
                    open = false
                    actions.onNote()
                },
                leadingIcon = {
                    Icon(if (actions.hasNote) Icons.Default.StickyNote2 else Icons.Default.EditNote, null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.media_compress_one)) },
                onClick = { open = false; actions.onCompress() },
                leadingIcon = { Icon(Icons.Default.Compress, null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.album_move)) },
                onClick = { open = false; actions.onMove() },
                leadingIcon = { Icon(Icons.Default.Folder, null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_delete)) },
                onClick = {
                    open = false
                    actions.onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun videoDetails(video: Video): String =
    "${formatDuration(video.duration)} · ${android.text.format.Formatter.formatShortFileSize(LocalContext.current, video.fileSize)}"
