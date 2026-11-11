package ru.spgsroot.vibeplayer.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.manga.LibraryGallery
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.manga_search)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { viewModel.search(query) }) {
                            Icon(Icons.Default.Search, stringResource(R.string.manga_search))
                        }
                    }
                )
            }
            item {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text(stringResource(R.string.manga_favorites)) }
                )
            }
            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            item { Text(stringResource(R.string.manga_saved), style = MaterialTheme.typography.titleMedium) }
            if (shown.isEmpty()) {
                item { Text(stringResource(R.string.manga_none), style = MaterialTheme.typography.bodyMedium) }
            }
            items(shown, key = { it.id }) { item ->
                LibraryCard(
                    item = item,
                    onOpen = { navController.navigate("manga/${item.id}") },
                    onRemove = { viewModel.remove(item.id) }
                )
            }
            if (search.isNotEmpty()) {
                item { Text(stringResource(R.string.manga_results), style = MaterialTheme.typography.titleMedium) }
                items(search, key = { "search-${it.id}" }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { navController.navigate("manga/${item.id}") }) {
                                Text(stringResource(R.string.manga_open))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    item: LibraryGallery,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.favorite) {
                    Icon(Icons.Default.Favorite, stringResource(R.string.manga_favorites))
                }
            }
            Text("${item.pagePaths.size}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRemove) { Text(stringResource(R.string.manga_remove)) }
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
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) {
        if (!opened) {
            opened = true
            return@LaunchedEffect
        }
        if (pages.isNotEmpty()) viewModel.onPageTurned()
    }
    val favorite = library.any { it.id == galleryId && it.favorite }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.title ?: stringResource(R.string.manga_reader),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            stringResource(R.string.manga_favorites)
                        )
                    }
                    TextButton(onClick = { viewModel.saveCurrent() }) {
                        Text(stringResource(R.string.manga_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            progress?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
            Text(
                stringResource(R.string.manga_haptic),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
            Slider(
                value = intensity,
                onValueChange = viewModel::setIntensity,
                valueRange = 0.1f..1f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (pages.isEmpty()) {
                Text(stringResource(R.string.manga_empty), modifier = Modifier.padding(16.dp))
            } else {
                Text(
                    stringResource(R.string.manga_page, pager.currentPage + 1, pages.size),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium
                )
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    val path = pages[page]
                    AsyncImage(
                        model = viewModel.pageModel(path) ?: NhentaiCdn.image(path),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
