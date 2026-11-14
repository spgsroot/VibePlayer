package ru.spgsroot.peachy.ui.player

import android.text.format.Formatter
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.decode.VideoFrameDecoder
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.domain.model.Video
@OptIn(ExperimentalCoilApi::class)

@Composable
fun QueueCarousel(queue: List<Video>, currentId: Long?, onSelect: (Video) -> Unit) {
    val context = LocalContext.current
    val state = rememberLazyListState()
    LaunchedEffect(queue, currentId) {
        val index = queue.indexOfFirst { it.id == currentId }
        if (index >= 0) state.animateScrollToItem(index)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        if (queue.isEmpty()) Text(stringResource(R.string.gallery_search_empty), Modifier.padding(12.dp))
        else LazyRow(state = state, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(queue, key = { it.id }) { video ->
                Card(onClick = { onSelect(video) }, modifier = Modifier.width(128.dp).then(
                    if (video.id == currentId) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier
                )) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(video.thumbnailPath ?: video.filePath).size(384, 168)
                            .apply { if (video.thumbnailPath == null) { decoderFactory(VideoFrameDecoder.Factory()); videoFrameMillis(3000) } }.build(),
                        contentDescription = video.title, modifier = Modifier.fillMaxWidth().height(56.dp), contentScale = ContentScale.Crop
                    )
                    Text(video.title, Modifier.padding(horizontal = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    Text(Formatter.formatShortFileSize(context, video.fileSize), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
