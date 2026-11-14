package ru.spgsroot.peachy.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.data.db.PhotoEntity
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoItemCard(
    photo: PhotoEntity,
    layout: GalleryLayout,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val imageData by produceState(photo.filePath, photo.thumbnailPath, photo.filePath) {
        value = withContext(Dispatchers.IO) {
            photo.thumbnailPath?.takeIf { File(it).exists() } ?: photo.filePath
        }
    }
    val click = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    if (layout == GalleryLayout.List) {
        Card(
            modifier = Modifier.fillMaxWidth().then(click),
            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(imageData, photo.title, Modifier.width(128.dp).aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    PhotoTitle(photo.title)
                    Icon(Icons.Default.Image, stringResource(R.string.gallery_photo), modifier = Modifier.size(16.dp))
                    Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, photo.fileSize),
                        style = MaterialTheme.typography.labelSmall)
                }
                if (isSelected) Checkbox(true, null)
                else PhotoMenu(onRename, onDelete, onMove, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(if (layout == GalleryLayout.Large) 0.7f else 16f / 9f).then(click),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(imageData, photo.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Icon(Icons.Default.Image, stringResource(R.string.gallery_photo), tint = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp)
                            .background(Color.Black.copy(alpha = 0.6f)))
                    if (isSelected) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                            Checkbox(true, null, Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                    } else {
                        Box(Modifier.align(Alignment.TopEnd)) { PhotoMenu(onRename, onDelete, onMove, Color.White) }
                    }
                }
            }
            PhotoTitle(photo.title)
            Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, photo.fileSize),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PhotoTitle(title: String, color: Color = Color.Unspecified) {
    Text(title, color = color, style = MaterialTheme.typography.bodyMedium,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PhotoMenu(onRename: () -> Unit, onDelete: () -> Unit, onMove: () -> Unit, tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.content_description_more), tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_rename)) },
                onClick = { open = false; onRename() },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.album_move)) }, onClick = { open = false; onMove() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_delete)) },
                onClick = { open = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            )
        }
    }
}
