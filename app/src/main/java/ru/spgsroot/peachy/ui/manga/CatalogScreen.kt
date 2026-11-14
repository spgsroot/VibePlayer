package ru.spgsroot.peachy.ui.manga

import ru.spgsroot.peachy.manga.TagNames
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.manga.CatalogQuery
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import ru.spgsroot.peachy.manga.GallerySummary
import ru.spgsroot.peachy.manga.GalleryTag
import ru.spgsroot.peachy.manga.NhentaiCdn
import ru.spgsroot.peachy.manga.SearchDestination
import ru.spgsroot.peachy.manga.TagRef
import ru.spgsroot.peachy.manga.cycled
import ru.spgsroot.peachy.manga.TagMode
import ru.spgsroot.peachy.manga.nextTagMode
import ru.spgsroot.peachy.manga.searchDestination

private const val CARD_TAG_LIMIT = 4
private const val COVER_ASPECT = 0.7f
/** How many items before the end of the list the next page starts loading. */
private const val LOAD_MORE_AHEAD = 6

@Composable
fun CatalogScreen(
    viewModel: MangaViewModel,
    onOpen: (Int) -> Unit,
    showFilters: Boolean,
    onFiltersDismiss: () -> Unit,
    text: String,
    onTextChange: (String) -> Unit
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val queries by viewModel.queries.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()
    val uploadDates by viewModel.uploadDates.collectAsStateWithLifecycle()
    val blockedNames by viewModel.blockedNames.collectAsStateWithLifecycle()
    val tagLibrary by viewModel.tagLibrary.collectAsStateWithLifecycle()
    val favoriteTags = remember(tagLibrary) { tagLibrary.favorites.toSet() }
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }

    fun submit(query: CatalogQuery) {
        when (val destination = searchDestination(query.text)) {
            is SearchDestination.OpenGallery -> onOpen(destination.id)
            SearchDestination.RunSearch -> viewModel.search(query)
        }
    }

    // Opening the catalog shows the newest galleries instead of an empty prompt.
    LaunchedEffect(Unit) {
        if (!catalog.searched && !catalog.loading) viewModel.search(catalog.query)
    }

    // Scrolling near the end appends the next page. Every state change re-checks, so a page that leaves the list
    // short keeps loading; a failed page waits for the retry button instead of looping.
    LaunchedEffect(nearEnd, catalog.page, catalog.searched, catalog.loading, catalog.loadingMore) {
        if (nearEnd) viewModel.loadMore()
    }

    if (showFilters) {
        FilterSheet(
            query = catalog.query.copy(text = text),
            blockedIds = blockedIds,
            blockedNames = blockedNames,
            onBlockId = viewModel::blockId,
            onUnblockId = viewModel::unblockId,
            onBlockName = viewModel::blockName,
            onUnblockName = viewModel::unblockName,
            onDismiss = onFiltersDismiss,
            onApply = { query ->
                onFiltersDismiss()
                submit(query)
            }
        )
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (catalog.query.tags.isNotEmpty()) fullWidth {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catalog.query.tags, key = { it.type + ":" + it.name }) { filter ->
                        AssistChip(
                            onClick = {
                                val next = nextTagMode(filter.mode)
                                val tags = catalog.query.tags.mapNotNull {
                                    if (it != filter) it else next?.let { mode -> it.copy(mode = mode) }
                                }
                                submit(catalog.query.copy(tags = tags))
                            },
                            label = {
                                val sign = if (filter.mode == TagMode.Exclude) "-" else "#"
                                Text(
                                    sign + TagNames.display(filter.type, filter.name, tagLanguage()),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
        if (queries.isNotEmpty() && catalog.query.tags.isEmpty()) {
            fullWidth {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.manga_recent_queries), style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(queries.take(10), key = { it }) { previous ->
                            AssistChip(
                                onClick = {
                                    onTextChange(previous)
                                    submit(CatalogQuery(text = previous, sort = catalog.query.sort))
                                },
                                label = { Text(previous, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }
        when {
            catalog.loading -> fullWidth {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            catalog.failed -> fullWidth {
                Column {
                    Text(stringResource(R.string.manga_search_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.search(catalog.query, catalog.page) }) {
                        Text(stringResource(R.string.manga_retry))
                    }
                }
            }
            catalog.results.isEmpty() -> fullWidth {
                Text(stringResource(if (catalog.searched) R.string.manga_no_results else R.string.manga_catalog_hint))
            }
        }
        items(catalog.results, key = { it.id }) { gallery ->
            GalleryGridCard(
                title = gallery.title,
                thumbnailPath = gallery.thumbnailPath,
                pages = gallery.numPages,
                tags = gallery.tags,
                favorites = gallery.numFavorites,
                uploadedAt = uploadDates[gallery.id],
                onOpen = { onOpen(gallery.id) },
                onTag = { tag ->
                    submit(catalog.query.copy(text = text, tags = catalog.query.tags.cycled(tag.type, tag.name)))
                },
                onTagLong = { tag -> viewModel.toggleFavoriteTag(TagRef(tag.type, tag.name)) },
                favoriteTags = favoriteTags
            )
        }
        if (catalog.loadingMore) fullWidth {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (catalog.moreFailed) fullWidth {
            Column {
                Text(stringResource(R.string.manga_search_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.loadMore(retry = true) }) {
                    Text(stringResource(R.string.manga_retry))
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * The large cover card used by the catalog, For you and History. [onTag] null leaves the chips as plain labels;
 * [onTagLong] marks a tag as a favorite and [favoriteTags] are drawn highlighted.
 */
@Composable
internal fun GalleryGridCard(
    title: String,
    thumbnailPath: String,
    pages: Int,
    tags: List<GalleryTag>,
    favorites: Int?,
    uploadedAt: Long?,
    onOpen: () -> Unit,
    onTag: ((GalleryTag) -> Unit)?,
    onTagLong: ((GalleryTag) -> Unit)? = null,
    favoriteTags: Set<TagRef> = emptySet()
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT)) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = NhentaiCdn.thumb(thumbnailPath),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                favorites?.let { count ->
                    Text(
                        text = "\u2665 " + compactCount(count),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        GalleryMetaLine(tags, pages, uploadedAt)
        OneLineTagChips(cardTags(tags), onTag, onTagLong, favoriteTags)
    }
}

/** Language, page count and, when the app has seen one, the upload date, on a single line. */
@Composable
internal fun GalleryMetaLine(tags: List<GalleryTag>, pages: Int, uploadedAt: Long?) {
    val language = tags.firstOrNull { it.type == "language" && it.name != "translated" }
        ?.let { TagNames.display(it, tagLanguage()) }
    val parts = listOfNotNull(
        language,
        stringResource(R.string.manga_page_count, pages),
        uploadedAt?.let(::formatUploadDate)
    )
    Text(
        text = parts.joinToString(" \u00b7 "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal fun formatUploadDate(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun cardTags(tags: List<GalleryTag>): List<GalleryTag> = tags
    .filter { it.type != "language" && it.type != "category" }
    .sortedBy { if (it.type == "tag") 0 else 1 }
    .take(CARD_TAG_LIMIT)

/**
 * Small chips in one row that never wrap or grow tall: a chip that would not fit whole is left out, so no chip
 * is ever shown cut off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OneLineTagChips(
    tags: List<GalleryTag>,
    onTag: ((GalleryTag) -> Unit)?,
    onTagLong: ((GalleryTag) -> Unit)? = null,
    favoriteTags: Set<TagRef> = emptySet()
) {
    if (tags.isEmpty()) return
    val gap = 4.dp
    val language = tagLanguage()
    Layout(
        content = {
            tags.forEach { tag ->
                val favorite = TagRef(tag.type, tag.name) in favoriteTags
                Text(
                    "#" + TagNames.display(tag, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (favorite) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (favorite) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .let { chip ->
                            if (onTag == null) chip else chip.combinedClickable(
                                onClick = { onTag(tag) },
                                onLongClick = onTagLong?.let { long -> { long(tag) } }
                            )
                        }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        var used = 0
        val shown = ArrayList<Placeable>(placeables.size)
        for (placeable in placeables) {
            val next = if (shown.isEmpty()) placeable.width else used + gapPx + placeable.width
            if (next > constraints.maxWidth) break
            used = next
            shown += placeable
        }
        val height = shown.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            shown.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + gapPx
            }
        }
    }
}

private fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}
