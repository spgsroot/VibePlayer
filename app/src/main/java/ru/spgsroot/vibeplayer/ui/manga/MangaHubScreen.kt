package ru.spgsroot.vibeplayer.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ru.spgsroot.vibeplayer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaHubScreen(
    navController: NavController,
    viewModel: MangaViewModel = hiltViewModel()
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabState = rememberSaveableStateHolder()
    val labels = listOf(R.string.manga_catalog, R.string.manga_library, R.string.manga_history)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_hub)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                labels.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            tabState.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> CatalogScreen(viewModel, onOpen = { navController.navigate("manga/$it") })
                    1 -> MangaLibraryScreen(navController, viewModel)
                    2 -> HistoryScreen(viewModel, onOpen = { navController.navigate("manga/$it") })
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(viewModel: MangaViewModel, onOpen: (Int) -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (history.isEmpty()) item { Text(stringResource(R.string.manga_history_empty)) }
        items(history, key = { it.galleryId }) { entry ->
            CatalogGalleryCard(
                title = entry.title,
                thumbnailPath = entry.thumbnailPath,
                pageCount = entry.pageCount,
                onOpen = { onOpen(entry.galleryId) }
            )
        }
    }
}
