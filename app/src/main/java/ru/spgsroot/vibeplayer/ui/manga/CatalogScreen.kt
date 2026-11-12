package ru.spgsroot.vibeplayer.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.manga.CatalogQuery
import ru.spgsroot.vibeplayer.manga.NhentaiCdn
import ru.spgsroot.vibeplayer.manga.SearchDestination
import ru.spgsroot.vibeplayer.manga.searchDestination

@Composable
fun CatalogScreen(viewModel: MangaViewModel, onOpen: (Int) -> Unit) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val queries by viewModel.queries.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()
    val blockedNames by viewModel.blockedNames.collectAsStateWithLifecycle()
    var text by rememberSaveable(catalog.query.text) { mutableStateOf(catalog.query.text) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    fun submit(query: CatalogQuery) {
        when (val destination = searchDestination(query.text)) {
            is SearchDestination.OpenGallery -> onOpen(destination.id)
            SearchDestination.RunSearch -> viewModel.search(query)
        }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
        item {
            TextButton(onClick = { showFilters = true }) { Text(stringResource(R.string.manga_filters)) }
        }
        if (queries.isNotEmpty()) {
            item {
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
        when {
            catalog.loading -> item { CircularProgressIndicator() }
            catalog.failed -> item {
                Text(stringResource(R.string.manga_search_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.search(catalog.query, catalog.page) }) {
                    Text(stringResource(R.string.manga_retry))
                }
            }
            catalog.results.isEmpty() -> item {
                Text(stringResource(if (catalog.searched) R.string.manga_no_results else R.string.manga_catalog_hint))
            }
        }
        items(catalog.results, key = { it.id }) { gallery ->
            CatalogGalleryCard(gallery.title, gallery.thumbnailPath, gallery.numPages, onOpen = { onOpen(gallery.id) })
        }
        if (catalog.searched) item {
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

@Composable
internal fun CatalogGalleryCard(title: String, thumbnailPath: String, pageCount: Int, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = NhentaiCdn.thumb(thumbnailPath),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.manga_page_count, pageCount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
