package ru.spgsroot.vibeplayer.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.manga.NhentaiCdn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaLibraryScreen(
    navController: NavController,
    viewModel: MangaViewModel = hiltViewModel()
) {
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    val shown = if (favoritesOnly) library.filter { it.favorite } else library

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_library)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.manga_search)) },
                    singleLine = true
                )
                Button(onClick = { viewModel.search(query) }) {
                    Text(stringResource(R.string.manga_search))
                }
            }
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text(stringResource(R.string.manga_favorites)) }
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { item ->
                    Text(item.title, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { navController.navigate("manga/${item.id}") }) {
                            Text(stringResource(R.string.manga_open))
                        }
                        Button(onClick = { viewModel.remove(item.id) }) {
                            Text(stringResource(R.string.manga_remove))
                        }
                    }
                }
                items(search, key = { "search-${it.id}" }) { item ->
                    Text(item.title)
                    Button(onClick = { navController.navigate("manga/${item.id}") }) {
                        Text(stringResource(R.string.manga_open))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    galleryId: Int,
    navController: NavController,
    viewModel: MangaViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val intensity by viewModel.intensity.collectAsStateWithLifecycle()
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    LaunchedEffect(galleryId) { viewModel.open(galleryId) }
    val pages = detail?.pagePaths.orEmpty()
    val pager = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    LaunchedEffect(pager.currentPage) {
        if (pages.isNotEmpty()) viewModel.onPageTurned()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: stringResource(R.string.manga_reader)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        val favorite = library.any { it.id == galleryId && it.favorite }
                        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                    }
                    Button(onClick = { viewModel.saveCurrent() }) {
                        Text(stringResource(R.string.manga_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            progress?.let { Text(it, modifier = Modifier.padding(12.dp)) }
            Slider(
                value = intensity,
                onValueChange = viewModel::setIntensity,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            if (pages.isEmpty()) {
                Text(stringResource(R.string.manga_empty), modifier = Modifier.padding(12.dp))
            } else {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    val path = pages[page]
                    val model = viewModel.pageModel(path) ?: NhentaiCdn.image(path)
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
