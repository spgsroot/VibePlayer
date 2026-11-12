package ru.spgsroot.peachy.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.manga.GalleryTag

@Composable
fun ForYouScreen(
    onOpen: (Int) -> Unit,
    onTag: (GalleryTag) -> Unit,
    viewModel: ForYouViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!state.hasTaste && state.loaded) {
                    Text(
                        stringResource(R.string.manga_for_you_cold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = viewModel::refresh, enabled = !state.loading) {
                    Text(stringResource(R.string.manga_for_you_refresh))
                }
            }
        }
        when {
            state.loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.manga_search_error), color = MaterialTheme.colorScheme.error)
            }
            state.loaded && state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.manga_for_you_empty))
            }
        }
        items(state.items, key = { it.gallery.id }) { recommendation ->
            val gallery = recommendation.gallery
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GalleryGridCard(
                    title = gallery.title,
                    thumbnailPath = gallery.thumbnailPath,
                    pages = gallery.numPages,
                    tags = gallery.tags,
                    favorites = gallery.numFavorites,
                    uploadedAt = null,
                    onOpen = { onOpen(gallery.id) },
                    onTag = onTag
                )
                if (recommendation.because.isNotEmpty()) {
                    Text(
                        stringResource(R.string.manga_for_you_because, recommendation.because.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { viewModel.notInterested(gallery) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.manga_for_you_not_interested), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
