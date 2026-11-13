package ru.spgsroot.peachy.ui.manga

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.data.db.SavedPageEntity

private fun keyOf(page: SavedPageEntity) = "${page.galleryId}:${page.page}"

/** Pages the user kept from the reader. Tap opens the reader there; long-press selects. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedPagesScreen(
    navController: NavController,
    viewModel: MangaViewModel,
    snackbar: SnackbarHostState
) {
    val pages by viewModel.savedPages.collectAsStateWithLifecycle()
    val result by viewModel.pageExport.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    // A page removed elsewhere must not stay selected, or export would count a ghost.
    val chosen = pages.filter { keyOf(it) in selected }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.exportSavedPages(uri, chosen)
    }
    val done = stringResource(R.string.saved_pages_export_done, result?.exported ?: 0)
    val partial = stringResource(R.string.saved_pages_export_partial, result?.exported ?: 0, result?.failed?.size ?: 0)
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        selected = emptySet()
        snackbar.showSnackbar(if (r.failed.isEmpty()) done else partial)
        viewModel.consumePageExport()
    }

    Column(Modifier.fillMaxSize()) {
        if (chosen.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Default.Close, stringResource(R.string.btn_cancel))
                    }
                    Text(
                        stringResource(R.string.saved_pages_selected, chosen.size),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { launcher.launch(null) }) {
                        Icon(Icons.Default.SaveAlt, stringResource(R.string.saved_pages_export))
                    }
                    IconButton(onClick = {
                        chosen.forEach(viewModel::removeSavedPage)
                        selected = emptySet()
                    }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.saved_pages_delete))
                    }
                }
            }
        }
        if (pages.isEmpty()) {
            Text(
                stringResource(R.string.saved_pages_empty),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pages, key = { keyOf(it) }) { page ->
                    val isSelected = keyOf(page) in selected
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .aspectRatio(0.7f)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                if (isSelected) 3.dp else 0.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = if (isSelected) selected - keyOf(page) else selected + keyOf(page)
                                    } else {
                                        navController.navigate("manga/${page.galleryId}/read?page=${page.page}")
                                    }
                                },
                                onLongClick = { selected = selected + keyOf(page) }
                            )
                    ) {
                        AsyncImage(
                            model = viewModel.savedPageFile(page),
                            contentDescription = page.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            "${page.title} · ${page.page + 1}",
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
