package ru.spgsroot.peachy.ui.manga

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.manga.GalleryDetail
import ru.spgsroot.peachy.manga.PageTurnGate
import ru.spgsroot.peachy.manga.PageTurnHaptic
import ru.spgsroot.peachy.manga.ReaderFit
import ru.spgsroot.peachy.manga.ReaderOrientation
import ru.spgsroot.peachy.manga.ReaderSettings
import ru.spgsroot.peachy.manga.ReaderSettingsStore
import ru.spgsroot.peachy.manga.TapZone
import ru.spgsroot.peachy.manga.centerPage
import ru.spgsroot.peachy.manga.startPage
import ru.spgsroot.peachy.manga.tapZone

/** Pages fetched ahead of the one on screen, so turning the page does not wait for the network. */
private const val PRELOAD_PAGES = 3

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NativeReader(
    gallery: GalleryDetail,
    initialPage: Int,
    navController: NavController,
    viewModel: MangaViewModel
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("manga_reader", Context.MODE_PRIVATE) }
    var settings by remember { mutableStateOf(ReaderSettingsStore.load(prefs)) }
    fun change(next: ReaderSettings) {
        settings = next
        ReaderSettingsStore.save(prefs, next)
    }
    val intensity by viewModel.intensity.collectAsStateWithLifecycle()
    val library by viewModel.libraryItems.collectAsStateWithLifecycle()
    val savedPages by viewModel.savedPages.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var chrome by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var currentPage by rememberSaveable { mutableIntStateOf(startPage(initialPage, gallery.pagePaths.size)) }
    var hints by remember { mutableStateOf(!settings.hideHints) }
    val gate = remember { PageTurnGate() }
    val zoom = remember { ReaderZoom() }
    val latestGallery by rememberUpdatedState(gallery)
    val scope = rememberCoroutineScope()
    val thumbnails = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val total = gallery.pagePaths.size
    val vertical = settings.orientation == ReaderOrientation.Vertical
    LaunchedEffect(viewModel) {
        viewModel.setIntensity(prefs.getFloat("intensity", PageTurnHaptic.DEFAULT_INTENSITY))
    }
    LaunchedEffect(chrome, currentPage) {
        if (chrome) thumbnails.animateScrollToItem(currentPage)
    }
    LaunchedEffect(Unit) {
        if (hints) {
            delay(2_500)
            hints = false
        }
    }

    // Mode-local states start at the current page when the layout changes.
    key(settings.continuous, settings.orientation) {
        val pager = rememberPagerState(initialPage = currentPage, pageCount = { total })
        val strip = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
        val stripMode = settings.continuous
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
        LaunchedEffect(visiblePage, settings.fit) { zoom.reset() }
        LaunchedEffect(visiblePage) {
            val loader = context.imageLoader
            for (ahead in 1..PRELOAD_PAGES) {
                val path = gallery.pagePaths.getOrNull(visiblePage + ahead) ?: break
                loader.enqueue(ImageRequest.Builder(context).data(viewModel.pageModel(gallery.id, path)).build())
            }
        }
        // A tall strip has no useful top and bottom zones, so it keeps left and right.
        val tapOnY = vertical && !stripMode
        val onTap: (Float, Float) -> Unit = { position, extent ->
            val zone = if (settings.tapToSwitch) tapZone(position, extent, settings.handSwap && !tapOnY) else TapZone.Chrome
            when (zone) {
                TapZone.Back -> jump(visiblePage - 1)
                TapZone.Chrome -> chrome = !chrome
                TapZone.Forward -> jump(visiblePage + 1)
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val viewportHeight = maxHeight
            val scrollable = !zoom.zoomed
            val page: @Composable (Int, Boolean, Modifier) -> Unit = { index, inStrip, modifier ->
                ReaderPage(
                    model = remember(gallery.id, gallery.pagePaths[index]) {
                        viewModel.pageModel(gallery.id, gallery.pagePaths[index])
                    },
                    page = index,
                    active = index == visiblePage,
                    zoom = zoom,
                    fillWidth = settings.fit == ReaderFit.Width,
                    strip = inStrip,
                    tapOnY = tapOnY,
                    modifier = modifier,
                    viewportHeight = viewportHeight,
                    onTap = onTap
                )
            }
            when {
                stripMode && vertical -> LazyColumn(
                    state = strip,
                    userScrollEnabled = scrollable,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(total, key = { it }) { index ->
                        page(index, true, Modifier.fillMaxWidth().heightIn(min = viewportHeight))
                    }
                }
                stripMode -> LazyRow(
                    state = strip,
                    userScrollEnabled = scrollable,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(total, key = { it }) { index ->
                        page(index, false, Modifier.fillParentMaxSize())
                    }
                }
                vertical -> VerticalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = scrollable,
                    beyondViewportPageCount = 1
                ) { index -> page(index, false, Modifier.fillMaxSize()) }
                else -> HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = scrollable,
                    beyondViewportPageCount = 1
                ) { index -> page(index, false, Modifier.fillMaxSize()) }
            }
            if (hints && settings.tapToSwitch && !chrome) {
                TapHints(vertical = tapOnY, swapped = settings.handSwap && !tapOnY)
            }
            if (settings.inspect) {
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomButton(Icons.Default.ZoomIn, R.string.reader_zoom_in) { zoom.step(1) }
                    ZoomButton(Icons.Default.ZoomOut, R.string.reader_zoom_out) { zoom.step(-1) }
                }
            }
            if (chrome) {
                val pageSaved = savedPages.any { it.galleryId == gallery.id && it.page == visiblePage }
                TopAppBar(
                    title = {
                        Column {
                            Text(gallery.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.manga_page, visiblePage + 1, total),
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
                        IconButton(onClick = { viewModel.toggleSavedPage(visiblePage) }) {
                            Icon(
                                if (pageSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                stringResource(if (pageSaved) R.string.reader_unsave_page else R.string.reader_save_page)
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (library.any { it.id == gallery.id && it.favorite }) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                stringResource(R.string.manga_favorites)
                            )
                        }
                        IconButton(onClick = { sheet = true }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.manga_reader_settings))
                        }
                    }
                )
                Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ) {
                    Column(Modifier.navigationBarsPadding()) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                        progress?.let { Text(it, modifier = Modifier.padding(8.dp)) }
                        LazyRow(
                            state = thumbnails,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(total, key = { it }) { index ->
                                val selected = index == visiblePage
                                val shape = RoundedCornerShape(14.dp)
                                AsyncImage(
                                    model = remember(gallery.id, gallery.pagePaths[index]) {
                                        viewModel.pageModel(gallery.id, gallery.pagePaths[index])
                                    },
                                    contentDescription = stringResource(R.string.manga_jump_page, index + 1),
                                    modifier = Modifier
                                        .size(64.dp, 88.dp)
                                        .clip(shape)
                                        .alpha(if (selected) 1f else 0.55f)
                                        .border(
                                            if (selected) 2.dp else 0.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape
                                        )
                                        .clickable { jump(index) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (sheet) {
        Dialog(onDismissRequest = { sheet = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.manga_reader_settings),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { sheet = false }) {
                            Icon(Icons.Default.Close, stringResource(R.string.btn_close))
                        }
                    }
                    HorizontalDivider()
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingRow(
                            Icons.Default.SwapVert, R.string.reader_orientation,
                            onClick = {
                                change(settings.copy(orientation = if (vertical) ReaderOrientation.Horizontal else ReaderOrientation.Vertical))
                            }
                        ) {
                            Text(
                                stringResource(if (vertical) R.string.reader_vertical else R.string.reader_horizontal),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SettingRow(
                            Icons.Default.FitScreen, R.string.manga_reader_fit,
                            onClick = {
                                change(settings.copy(fit = if (settings.fit == ReaderFit.Contain) ReaderFit.Width else ReaderFit.Contain))
                            }
                        ) {
                            Text(
                                stringResource(if (settings.fit == ReaderFit.Contain) R.string.manga_fit_contain else R.string.manga_fit_width),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SwitchRow(Icons.Default.TouchApp, R.string.reader_tap_to_switch, settings.tapToSwitch) {
                            change(settings.copy(tapToSwitch = it))
                        }
                        SwitchRow(Icons.Default.SwapHoriz, R.string.reader_hand_swap, settings.handSwap) {
                            change(settings.copy(handSwap = it))
                        }
                        SwitchRow(Icons.Default.Search, R.string.reader_inspect, settings.inspect) {
                            change(settings.copy(inspect = it))
                        }
                        SwitchRow(Icons.Default.ViewAgenda, R.string.reader_continuous, settings.continuous) {
                            change(settings.copy(continuous = it))
                        }
                        SwitchRow(Icons.Default.VisibilityOff, R.string.reader_hide_hints, settings.hideHints) {
                            change(settings.copy(hideHints = it))
                        }
                        Text(stringResource(R.string.manga_haptic), modifier = Modifier.padding(top = 12.dp))
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
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null)
        Text(stringResource(label), Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, label, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, description: Int, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
        IconButton(onClick = onClick) {
            Icon(icon, stringResource(description), tint = Color.White)
        }
    }
}

/** Faint labels over the tap zones; shown for a moment when the reader opens. */
@Composable
private fun TapHints(vertical: Boolean, swapped: Boolean) {
    val first = if (swapped) R.string.reader_hint_next else R.string.reader_hint_back
    val last = if (swapped) R.string.reader_hint_back else R.string.reader_hint_next
    @Composable
    fun label(text: Int, modifier: Modifier) {
        Box(modifier.background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Text(stringResource(text), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
    if (vertical) {
        Column(Modifier.fillMaxSize()) {
            label(first, Modifier.weight(1f).fillMaxWidth())
            label(R.string.reader_hint_menu, Modifier.weight(1f).fillMaxWidth().alpha(0.5f))
            label(last, Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            label(first, Modifier.weight(1f).fillMaxSize())
            label(R.string.reader_hint_menu, Modifier.weight(1f).fillMaxSize().alpha(0.5f))
            label(last, Modifier.weight(1f).fillMaxSize())
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderPage(
    model: Any?,
    page: Int,
    active: Boolean,
    zoom: ReaderZoom,
    fillWidth: Boolean,
    strip: Boolean,
    tapOnY: Boolean,
    modifier: Modifier,
    viewportHeight: androidx.compose.ui.unit.Dp,
    onTap: (Float, Float) -> Unit
) {
    var aspect by remember(model) { mutableFloatStateOf(0.7f) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val latestTap by rememberUpdatedState(onTap)
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        if (active) zoom.transform(zoomChange, panChange)
    }
    LaunchedEffect(active, frame) {
        if (active) {
            zoom.width = frame.width.toFloat()
            zoom.height = frame.height.toFloat()
        }
    }
    Box(
        modifier.clipToBounds()
            .onSizeChanged { frame = it }
            .pointerInput(tapOnY) {
                detectTapGestures(
                    onTap = {
                        if (tapOnY) latestTap(it.y, size.height.toFloat()) else latestTap(it.x, size.width.toFloat())
                    },
                    onDoubleTap = { if (active) zoom.doubleTap(it) }
                )
            }
            .transformable(state = transform, canPan = { zoom.zoomed }, enabled = active),
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
                    if (active) {
                        scaleX = zoom.zoom
                        scaleY = zoom.zoom
                        translationX = zoom.pan.x
                        translationY = zoom.pan.y
                    }
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

