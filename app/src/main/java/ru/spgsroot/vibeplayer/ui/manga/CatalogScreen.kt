package ru.spgsroot.vibeplayer.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.manga.CatalogQuery
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import ru.spgsroot.vibeplayer.manga.GallerySummary
import ru.spgsroot.vibeplayer.manga.GalleryTag
import ru.spgsroot.vibeplayer.manga.NhentaiCdn
import ru.spgsroot.vibeplayer.manga.SearchDestination
import ru.spgsroot.vibeplayer.manga.TagFilter
import ru.spgsroot.vibeplayer.manga.TagMode
import ru.spgsroot.vibeplayer.manga.nextTagMode
import ru.spgsroot.vibeplayer.manga.searchDestination

private const val CARD_TAG_LIMIT = 4
private const val COVER_ASPECT = 0.7f

@Composable
fun CatalogScreen(viewModel: MangaViewModel, onOpen: (Int) -> Unit) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val queries by viewModel.queries.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()
    val uploadDates by viewModel.uploadDates.collectAsStateWithLifecycle()
    val blockedNames by viewModel.blockedNames.collectAsStateWithLifecycle()
    var text by rememberSaveable(catalog.query.text) { mutableStateOf(catalog.query.text) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

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

    if (showFilters) {
        FilterSheet(
            query = catalog.query.copy(text = text),
            blockedIds = blockedIds,
            blockedNames = blockedNames,
            onBlockId = viewModel::blockId,
            onUnblockId = viewModel::unblockId,
            onBlockName = viewModel::blockName,
            onUnblockName = viewModel::unblockName,
            onDismiss = { showFilters = false },
            onApply = { query ->
                showFilters = false
                submit(query)
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        fullWidth {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.manga_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(catalog.query.copy(text = text)) }),
                trailingIcon = {
                    IconButton(onClick = { submit(catalog.query.copy(text = text)) }) {
                        Icon(Icons.Default.Search, stringResource(R.string.manga_search))
                    }
                }
            )
        }
        fullWidth {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showFilters = true }) { Text(stringResource(R.string.manga_filters)) }
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
                                Text(sign + filter.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    text = previous
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
                    val filter = TagFilter(tag.type, tag.name, TagMode.Include)
                    val tags = catalog.query.tags.filterNot { it.type == filter.type && it.name == filter.name }
                    submit(catalog.query.copy(text = text, tags = tags + filter))
                }
            )
        }
        if (catalog.searched) fullWidth {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    enabled = !catalog.loading && catalog.page > 1,
                    onClick = { viewModel.search(catalog.query, catalog.page - 1) }
                ) { Text(stringResource(R.string.manga_previous)) }
                Text(stringResource(R.string.manga_catalog_page, catalog.page))
                TextButton(
                    enabled = !catalog.loading && !catalog.failed && catalog.hasNext,
                    onClick = { viewModel.search(catalog.query, catalog.page + 1) }
                ) { Text(stringResource(R.string.manga_next)) }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The large cover card used by the catalog and by History. [onTag] null leaves the chips as plain labels. */
@Composable
internal fun GalleryGridCard(
    title: String,
    thumbnailPath: String,
    pages: Int,
    tags: List<GalleryTag>,
    favorites: Int?,
    uploadedAt: Long?,
    onOpen: () -> Unit,
    onTag: ((GalleryTag) -> Unit)?
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
        OneLineTagChips(cardTags(tags), onTag)
    }
}

/** Language, page count and, when the app has seen one, the upload date, on a single line. */
@Composable
internal fun GalleryMetaLine(tags: List<GalleryTag>, pages: Int, uploadedAt: Long?) {
    val language = tags.firstOrNull { it.type == "language" && it.name != "translated" }?.name
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
@Composable
internal fun OneLineTagChips(tags: List<GalleryTag>, onTag: ((GalleryTag) -> Unit)?) {
    if (tags.isEmpty()) return
    val gap = 4.dp
    Layout(
        content = {
            tags.forEach { tag ->
                Text(
                    "#" + tag.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .let { chip -> if (onTag == null) chip else chip.clickable { onTag(tag) } }
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
