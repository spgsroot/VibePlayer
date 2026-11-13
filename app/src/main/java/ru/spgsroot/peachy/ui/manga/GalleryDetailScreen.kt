package ru.spgsroot.peachy.ui.manga

import ru.spgsroot.peachy.manga.TagNames
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.manga.GalleryComment
import ru.spgsroot.peachy.manga.GalleryDetail
import ru.spgsroot.peachy.manga.GalleryTag
import ru.spgsroot.peachy.manga.NhentaiCdn
import ru.spgsroot.peachy.manga.TagFilter
import ru.spgsroot.peachy.manga.TagMode
import ru.spgsroot.peachy.ui.dialog.NoteDialog
import java.text.DateFormat
import java.util.Date

private const val COVER_RATIO = 0.7f
private val groupOrder = listOf("parody", "character", "tag", "artist", "group", "language", "category")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryDetailScreen(
    galleryId: Int,
    navController: NavController,
    viewModel: GalleryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    LaunchedEffect(galleryId) { viewModel.load(galleryId) }
    val saved = library.any { it.id == galleryId }
    val favorite = library.any { it.id == galleryId && it.favorite }
    val detail = state.detail
    val note by remember(galleryId) { viewModel.note(galleryId) }.collectAsStateWithLifecycle(initialValue = null)
    var editingNote by remember { mutableStateOf(false) }
    if (editingNote) {
        NoteDialog(
            title = stringResource(R.string.note_title_gallery),
            initial = note.orEmpty(),
            onSave = { text ->
                viewModel.saveNote(galleryId, text)
                editingNote = false
            },
            onDismiss = { editingNote = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        when {
            detail == null && state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            detail == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.manga_retry)) }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Header(detail) }
                item {
                    Actions(
                        detail = detail,
                        savedPage = state.savedPage,
                        saved = saved,
                        favorite = favorite,
                        progress = progress,
                        onRead = { navController.navigate("manga/$galleryId/read") },
                        onDownload = viewModel::download,
                        onDeleteDownload = viewModel::removeDownload,
                        onFavorite = viewModel::toggleFavorite,
                        hasNote = note != null,
                        onNote = { editingNote = true }
                    )
                }
                actionError?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                groupedTags(detail.tags).forEach { (type, tags) ->
                    item(key = "tags-$type") {
                        TagGroup(type, tags) { tag ->
                            navController.searchCatalog(listOf(TagFilter(tag.type, tag.name, TagMode.Include)))
                        }
                    }
                }
                pagePreviewRows(detail) { page ->
                    navController.navigate("manga/$galleryId/read?page=$page")
                }
                item(key = "related") {
                    val related = state.related
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.manga_detail_related), style = MaterialTheme.typography.titleMedium)
                        when {
                            related.loading -> CircularProgressIndicator()
                            related.failed -> SectionError { viewModel.loadRelated(galleryId) }
                            related.loaded && related.items.isEmpty() -> Text(stringResource(R.string.manga_detail_related_empty))
                            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(related.items, key = { it.id }) { gallery ->
                                    RelatedCard(gallery.title, gallery.thumbnailPath) {
                                        navController.navigate("manga/${gallery.id}")
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "comments") {
                    val comments = state.comments
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.manga_detail_comments), style = MaterialTheme.typography.titleMedium)
                        when {
                            comments.loading -> CircularProgressIndicator()
                            comments.failed -> SectionError(viewModel::loadComments)
                            !comments.loaded -> OutlinedButton(onClick = viewModel::loadComments) {
                                Text(stringResource(R.string.manga_detail_comments_load))
                            }
                            comments.items.isEmpty() -> Text(stringResource(R.string.manga_detail_comments_empty))
                            else -> comments.items.forEach { CommentItem(it) }
                        }
                        Text(
                            stringResource(R.string.manga_detail_comments_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private const val PREVIEW_COLUMNS = 3

/** Thumbnails of every page, three per row, loaded lazily as the list scrolls; a tap opens the reader there. */
private fun LazyListScope.pagePreviewRows(detail: GalleryDetail, onOpenPage: (Int) -> Unit) {
    if (detail.pagePaths.isEmpty()) return
    item(key = "pages-title") {
        Text(stringResource(R.string.manga_detail_pages), style = MaterialTheme.typography.titleMedium)
    }
    val rows = detail.pagePaths.indices.chunked(PREVIEW_COLUMNS)
    items(rows.size, key = { "pages-$it" }) { rowIndex ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rows[rowIndex].forEach { page ->
                val thumb = detail.pageThumbs.getOrNull(page).orEmpty()
                AsyncImage(
                    model = if (thumb.isNotBlank()) NhentaiCdn.thumb(thumb) else NhentaiCdn.page(detail.pagePaths[page]),
                    contentDescription = stringResource(R.string.manga_jump_page, page + 1),
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(COVER_RATIO)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenPage(page) },
                    contentScale = ContentScale.Crop
                )
            }
            repeat(PREVIEW_COLUMNS - rows[rowIndex].size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Header(detail: GalleryDetail) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(
            model = NhentaiCdn.thumb(detail.thumbnailPath),
            contentDescription = null,
            modifier = Modifier.width(120.dp).aspectRatio(COVER_RATIO).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(detail.title, style = MaterialTheme.typography.titleLarge)
            detail.japaneseTitle?.takeIf { it.isNotBlank() && it != detail.title }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.manga_page_count, detail.pagePaths.size), style = MaterialTheme.typography.bodyMedium)
            detail.favoriteCount?.let { Text(stringResource(R.string.manga_detail_favorites_count, it), style = MaterialTheme.typography.bodyMedium) }
            detail.uploadedAt?.let {
                val date = remember(it) { DateFormat.getDateInstance().format(Date(it)) }
                Text(stringResource(R.string.manga_detail_uploaded, date), style = MaterialTheme.typography.bodyMedium)
            }
            detail.scanlator?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.manga_detail_scanlator, it), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Actions(
    detail: GalleryDetail,
    savedPage: Int?,
    saved: Boolean,
    favorite: Boolean,
    progress: String?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onFavorite: () -> Unit,
    hasNote: Boolean,
    onNote: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onRead, enabled = detail.pagePaths.isNotEmpty(), modifier = Modifier.weight(1f)) {
            val resume = savedPage?.takeIf { it in detail.pagePaths.indices && it > 0 }
            Text(
                if (resume == null) stringResource(R.string.manga_detail_read)
                else stringResource(R.string.manga_detail_continue, resume + 1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = if (saved) onDeleteDownload else onDownload,
            enabled = progress == null && detail.pagePaths.isNotEmpty()
        ) {
            Text(
                when {
                    progress != null -> stringResource(R.string.manga_detail_downloading, progress)
                    saved -> stringResource(R.string.manga_detail_delete_download)
                    else -> stringResource(R.string.manga_detail_download)
                },
                maxLines = 1
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                stringResource(R.string.manga_favorites),
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNote) {
            Icon(
                if (hasNote) Icons.Filled.StickyNote2 else Icons.Filled.EditNote,
                stringResource(R.string.content_description_note),
                tint = if (hasNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun groupedTags(tags: List<GalleryTag>): List<Pair<String, List<GalleryTag>>> {
    val byType = tags.groupBy { it.type }
    val known = groupOrder.mapNotNull { type -> byType[type]?.let { type to it } }
    val rest = byType.filterKeys { it !in groupOrder }.map { (type, list) -> type to list }
    return known + rest
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(type: String, tags: List<GalleryTag>, onTag: (GalleryTag) -> Unit) {
    val label = tagTypes.firstOrNull { it.first == type }?.second?.let { stringResource(it) } ?: type
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                Surface(
                    onClick = { onTag(tag) },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(TagNames.display(tag, tagLanguage()), style = MaterialTheme.typography.bodyMedium)
                        tag.count?.let {
                            Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedCard(title: String, thumbnailPath: String, onOpen: () -> Unit) {
    Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().aspectRatio(COVER_RATIO)) {
            AsyncImage(
                model = NhentaiCdn.thumb(thumbnailPath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionError(onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.manga_detail_section_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.manga_retry)) }
    }
}

@Composable
private fun CommentItem(comment: GalleryComment) {
    val text = remember(comment.id) {
        HtmlCompat.fromHtml(comment.body, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }
    val date = remember(comment.postedAt) { comment.postedAt?.let { DateFormat.getDateInstance().format(Date(it)) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                comment.username ?: stringResource(R.string.manga_detail_anonymous),
                style = MaterialTheme.typography.labelLarge
            )
            date?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
