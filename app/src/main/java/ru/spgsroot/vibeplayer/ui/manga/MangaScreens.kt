package ru.spgsroot.vibeplayer.ui.manga

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.manga.GalleryDetail
import ru.spgsroot.vibeplayer.manga.PageTurnGate
import ru.spgsroot.vibeplayer.manga.PageTurnHaptic
import ru.spgsroot.vibeplayer.manga.TapZone
import ru.spgsroot.vibeplayer.manga.centerPage
import ru.spgsroot.vibeplayer.manga.startPage
import ru.spgsroot.vibeplayer.manga.tapZone
import ru.spgsroot.vibeplayer.manga.LibraryGallery
import ru.spgsroot.vibeplayer.manga.NhentaiCdn

@Composable
fun MangaLibraryScreen(
    navController: NavController,
    viewModel: MangaViewModel,
    favoritesOnly: Boolean
) {
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val shown = if (favoritesOnly) library.filter { it.favorite } else library

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
    }
}

@Composable
private fun LibraryCard(
    item: LibraryGallery,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(item.thumbnailPath)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
}

@Composable
private fun Cover(path: String) {
    AsyncImage(
        model = NhentaiCdn.thumb(path),
        contentDescription = null,
        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun MangaReaderScreen(
    galleryId: Int,
    startAt: Int? = null,
    navController: NavController,
    viewModel: MangaViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(galleryId, startAt) { viewModel.open(galleryId, startAt) }
    val current = detail?.takeIf { it.id == galleryId }
    if (current != null && initialPage != null && current.pagePaths.isNotEmpty()) {
        key(galleryId) {
            NativeReader(current, initialPage!!, navController, viewModel)
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
            }
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                current != null -> Text(stringResource(R.string.manga_empty))
                else -> CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeReader(
    gallery: GalleryDetail,
    initialPage: Int,
    navController: NavController,
    viewModel: MangaViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(prefs.getString("mode", "paged") ?: "paged") }
    var fit by remember { mutableStateOf(prefs.getString("fit", "contain") ?: "contain") }
    var mirror by remember { mutableStateOf(prefs.getBoolean("mirror", false)) }
    val intensity by viewModel.intensity.collectAsStateWithLifecycle()
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var chrome by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var currentPage by rememberSaveable { mutableIntStateOf(startPage(initialPage, gallery.pagePaths.size)) }
    val gate = remember { PageTurnGate() }
    val latestGallery by rememberUpdatedState(gallery)
    val scope = rememberCoroutineScope()
    val thumbnails = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    LaunchedEffect(viewModel) {
        viewModel.setIntensity(prefs.getFloat("intensity", PageTurnHaptic.DEFAULT_INTENSITY))
    }
    LaunchedEffect(chrome, currentPage) {
        if (chrome) thumbnails.animateScrollToItem(currentPage)
    }

    // Mode-local states start at the current page when switching between pager and strip.
    key(mode) {
        val pager = rememberPagerState(initialPage = currentPage, pageCount = { gallery.pagePaths.size })
        val strip = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
        val stripMode = mode == "strip"
        val visiblePage by remember {
            derivedStateOf {
                if (stripMode) strip.centeredPage().takeIf { it >= 0 } ?: currentPage
                else pager.currentPage
            }
        }
        val jump: (Int) -> Unit = { target ->
            if (target in gallery.pagePaths.indices) {
                scope.launch {
                    if (stripMode) strip.animateScrollToItem(target)
                    else pager.animateScrollToPage(target)
                }
            }
        }
        LaunchedEffect(pager, strip) {
            if (stripMode) {
                snapshotFlow { strip.centeredPage() to !strip.isScrollInProgress }
                    .collect { (page, idle) ->
                        if (page >= 0) {
                            currentPage = page
                            if (gate.onPage(page, idle)) {
                                viewModel.onPageTurned()
                                viewModel.saveReadHistory(latestGallery, page)
                            }
                        }
                    }
            } else {
                snapshotFlow { pager.settledPage }.collect { page ->
                    currentPage = page
                    if (gate.onPage(page, idle = true)) {
                        viewModel.onPageTurned()
                        viewModel.saveReadHistory(latestGallery, page)
                    }
                }
            }
        }
        DisposableEffect(pager, strip) {
            onDispose {
                // Read layout now, not the last idle sample: Back can interrupt a fling.
                val page = if (stripMode) strip.centeredPage().takeIf { it >= 0 } ?: currentPage
                else pager.currentPage
                viewModel.saveReadHistory(latestGallery, page)
            }
        }
        val onTap: (Float, Float) -> Unit = { x, width ->
            when (tapZone(x, width, mirror)) {
                TapZone.Back -> jump(visiblePage - 1)
                TapZone.Chrome -> chrome = !chrome
                TapZone.Forward -> jump(visiblePage + 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val viewportHeight = maxHeight
            if (stripMode) {
                LazyColumn(state = strip, modifier = Modifier.fillMaxSize()) {
                    items(gallery.pagePaths.size, key = { it }) { page ->
                        ReaderPage(
                            model = remember(gallery.id, gallery.pagePaths[page]) {
                                viewModel.pageModel(gallery.id, gallery.pagePaths[page])
                            },
                            page = page,
                            active = page == visiblePage,
                            fillWidth = fit == "width",
                            strip = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = viewportHeight),
                            viewportHeight = viewportHeight,
                            onTap = onTap
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    ReaderPage(
                        model = remember(gallery.id, gallery.pagePaths[page]) {
                            viewModel.pageModel(gallery.id, gallery.pagePaths[page])
                        },
                        page = page,
                        active = page == visiblePage,
                        fillWidth = fit == "width",
                        strip = false,
                        modifier = Modifier.fillMaxSize(),
                        viewportHeight = viewportHeight,
                        onTap = onTap
                    )
                }
            }
            if (chrome) {
                TopAppBar(
                    title = {
                        Column {
                            Text(gallery.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.manga_page, visiblePage + 1, gallery.pagePaths.size),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (library.any { it.id == gallery.id && it.favorite }) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                stringResource(R.string.manga_favorites)
                            )
                        }
                        IconButton(onClick = { settings = true }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.manga_reader_settings))
                        }
                    }
                )
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Column(Modifier.navigationBarsPadding()) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                        progress?.let { Text(it, modifier = Modifier.padding(8.dp)) }
                        LazyRow(
                            state = thumbnails,
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(gallery.pagePaths.size, key = { it }) { page ->
                                Column(
                                    Modifier.width(56.dp)
                                        .border(
                                            if (page == visiblePage) 2.dp else 0.dp,
                                            if (page == visiblePage) MaterialTheme.colorScheme.primary else Color.Transparent
                                        )
                                        .clickable { jump(page) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = remember(gallery.id, gallery.pagePaths[page]) {
                                            viewModel.pageModel(gallery.id, gallery.pagePaths[page])
                                        },
                                        contentDescription = stringResource(R.string.manga_jump_page, page + 1),
                                        modifier = Modifier.size(56.dp, 72.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text("${page + 1}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (settings) {
        ModalBottomSheet(onDismissRequest = { settings = false }) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.manga_reader_settings), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.manga_reader_mode))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "paged",
                        onClick = { mode = "paged"; prefs.edit().putString("mode", mode).apply() },
                        label = { Text(stringResource(R.string.manga_mode_paged)) }
                    )
                    FilterChip(
                        selected = mode == "strip",
                        onClick = { mode = "strip"; prefs.edit().putString("mode", mode).apply() },
                        label = { Text(stringResource(R.string.manga_mode_strip)) }
                    )
                }
                Text(stringResource(R.string.manga_reader_fit))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = fit == "contain",
                        onClick = { fit = "contain"; prefs.edit().putString("fit", fit).apply() },
                        label = { Text(stringResource(R.string.manga_fit_contain)) }
                    )
                    FilterChip(
                        selected = fit == "width",
                        onClick = { fit = "width"; prefs.edit().putString("fit", fit).apply() },
                        label = { Text(stringResource(R.string.manga_fit_width)) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.manga_mirror), Modifier.weight(1f))
                    Switch(
                        checked = mirror,
                        onCheckedChange = { mirror = it; prefs.edit().putBoolean("mirror", it).apply() }
                    )
                }
                Text(stringResource(R.string.manga_haptic))
                Slider(
                    value = intensity,
                    onValueChange = {
                        viewModel.setIntensity(it)
                        prefs.edit().putFloat("intensity", it).apply()
                    },
                    valueRange = 0.1f..1f
                )
                TextButton(onClick = viewModel::saveCurrent) { Text(stringResource(R.string.manga_save)) }
            }
        }
    }
}

private fun LazyListState.centeredPage(): Int {
    val layout = layoutInfo
    val visible = layout.visibleItemsInfo
    val nearest = centerPage(
        visible.map { it.offset + it.size / 2 },
        (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    )
    return visible.getOrNull(nearest)?.index ?: -1
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReaderPage(
    model: Any?,
    page: Int,
    active: Boolean,
    fillWidth: Boolean,
    strip: Boolean,
    modifier: Modifier,
    viewportHeight: androidx.compose.ui.unit.Dp,
    onTap: (Float, Float) -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var aspect by remember(model) { mutableFloatStateOf(0.7f) }
    val latestTap by rememberUpdatedState(onTap)
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
        pan = if (zoom == 1f) Offset.Zero else pan + panChange
    }
    LaunchedEffect(active, fillWidth) {
        zoom = 1f
        pan = Offset.Zero
    }
    Box(
        modifier.clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { latestTap(it.x, size.width.toFloat()) },
                    onDoubleTap = { zoom = 1f; pan = Offset.Zero }
                )
            }
            .transformable(state = transform, canPan = { zoom > 1f }, enabled = active),
        contentAlignment = Alignment.Center
    ) {
        val imageModifier = if (fillWidth) Modifier.fillMaxWidth().aspectRatio(aspect)
        else Modifier.fillMaxWidth().height(viewportHeight)
        val container = if (fillWidth && !strip) {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        } else Modifier.fillMaxWidth()
        Box(container, contentAlignment = Alignment.Center) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.manga_jump_page, page + 1),
                modifier = imageModifier.graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
                contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
                onSuccess = {
                    val drawable = it.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        aspect = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                    }
                }
            )
        }
    }
}
