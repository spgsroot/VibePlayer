package ru.spgsroot.peachy.ui.gallery

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.domain.model.Video
import ru.spgsroot.peachy.compress.CompressionState
import ru.spgsroot.peachy.ui.dialog.CompressDialog
import ru.spgsroot.peachy.ui.dialog.CompressProgressDialog
import ru.spgsroot.peachy.ui.dialog.NoteDialog
import androidx.compose.ui.platform.LocalView
import ru.spgsroot.peachy.ui.HeaderSearch
import androidx.compose.material.icons.filled.Folder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val allItems by viewModel.items.collectAsState()
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val albums by viewModel.albums.collectAsState()
    var activeAlbum by rememberSaveable { mutableStateOf<Long?>(null) }
    var moveTargets by remember { mutableStateOf<List<GalleryIdentity>?>(null) }
    val items = remember(allItems, pager.currentPage, activeAlbum) { itemsForPage(allItems, pager.currentPage, activeAlbum) }
    LaunchedEffect(pager.currentPage, activeAlbum) { viewModel.clearSelection() }
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val videoNotes by viewModel.videoNotes.collectAsState()
    val isSelectionMode = selectedItems.isNotEmpty()
    val videoActionsEnabled = selectedItems.allowsVideoActions()
    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }
    BackHandler(enabled = !isSelectionMode && pager.currentPage == 3 && activeAlbum != null) { activeAlbum = null }
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val albumError by viewModel.albumError.collectAsState()
    val albumErrorText = stringResource(R.string.album_failed)
    LaunchedEffect(albumError) {
        if (albumError) { snackbarHostState.showSnackbar(albumErrorText); viewModel.albumError.value = false }
    }
    moveTargets?.let { chosen ->
        AlbumPicker(albums, { moveTargets = null }, { viewModel.moveToAlbum(chosen, it) }, viewModel::createAlbum)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.exportSelected(uri)
    }
    val exportedText = exportResult?.let { r ->
        if (r.failed.isEmpty()) {
            stringResource(R.string.gallery_export_done, r.exported)
        } else {
            stringResource(R.string.gallery_export_partial, r.exported, r.failed.size)
        }
    }
    LaunchedEffect(exportResult) {
        if (exportedText != null) {
            snackbarHostState.showSnackbar(exportedText)
            viewModel.consumeExportResult()
        }
    }
    val deleteFailures by viewModel.deleteFailures.collectAsState()
    val deleteFailedText = stringResource(R.string.gallery_delete_failed, deleteFailures)
    LaunchedEffect(deleteFailures) {
        if (deleteFailures > 0) {
            snackbarHostState.showSnackbar(deleteFailedText)
            viewModel.consumeDeleteFailures()
        }
    }
    val compression by viewModel.compression.collectAsState()
    val compressDialog by viewModel.compressDialog.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gallery", Context.MODE_PRIVATE) }
    var layout by remember { mutableStateOf(GalleryLayoutStore.load(prefs)) }
    var gridColumns by remember { mutableIntStateOf(GalleryLayoutStore.loadGridColumns(prefs)) }

    var videoToRename by remember { mutableStateOf<Video?>(null) }
    var videoForThumbnail by remember { mutableStateOf<Video?>(null) }
    var videoForNote by remember { mutableStateOf<Video?>(null) }
    var photoToRename by remember { mutableStateOf<PhotoEntity?>(null) }
    var viewedPhotoId by rememberSaveable { mutableStateOf<Long?>(null) }

    photoToRename?.let { photo ->
        EditVideoDialog(
            currentTitle = photo.title,
            titleRes = R.string.dialog_rename_photo,
            onDismiss = { photoToRename = null },
            onConfirm = { title ->
                viewModel.renamePhoto(photo, title)
                photoToRename = null
            }
        )
    }
    // Resolve from the live collection, so deleting a photo also dismisses its viewer.
    items.filterIsInstance<GalleryItem.PhotoItem>().firstOrNull { it.photo.id == viewedPhotoId }?.let {
        PhotoViewer(it.photo, onDismiss = { viewedPhotoId = null })
    }

    val thumbnailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && videoForThumbnail != null) {
            viewModel.updateThumbnail(videoForThumbnail!!, uri, context)
        }
        videoForThumbnail = null
    }

    if (videoToRename != null) {
        EditVideoDialog(
            currentTitle = videoToRename!!.title,
            onDismiss = { videoToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameVideo(videoToRename!!, newTitle)
                videoToRename = null
            }
        )
    }

    videoForNote?.let { video ->
        NoteDialog(
            title = stringResource(R.string.note_title_video, video.title),
            initial = videoNotes[video.id].orEmpty(),
            onSave = { text ->
                viewModel.saveNote(video.id, text)
                videoForNote = null
            },
            onDismiss = { videoForNote = null }
        )
    }

    compressDialog?.let { dialog ->
        CompressDialog(
            sources = dialog.sources,
            videoCount = dialog.count,
            onStart = viewModel::compressSelected,
            onDismiss = viewModel::closeCompress
        )
    }
    (compression as? CompressionState.Running)?.let { running ->
        CompressProgressDialog(running, onCancel = viewModel::cancelCompression)
    }
    val view = LocalView.current
    val compressing = compression is CompressionState.Running
    DisposableEffect(compressing) {
        view.keepScreenOn = compressing
        onDispose { view.keepScreenOn = false }
    }
    val finished = compression as? CompressionState.Finished
    val finishedText = finished?.let {
        val saved = android.text.format.Formatter.formatFileSize(context, it.savedBytes)
        if (it.cancelled) {
            stringResource(R.string.compress_result_cancelled, saved, it.compressed)
        } else {
            stringResource(R.string.compress_result, saved, it.compressed, it.skipped, it.failed)
        }
    }
    LaunchedEffect(finished) {
        if (finishedText != null) {
            snackbarHostState.showSnackbar(finishedText)
            viewModel.consumeCompressionResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val progress = exportProgress
                    if (progress != null) {
                        Text(stringResource(R.string.gallery_exporting, progress.first, progress.second))
                    } else if (isSelectionMode) {
                        Text(stringResource(R.string.gallery_selected, selectedItems.size))
                    } else {
                        HeaderSearch(query, viewModel::setQuery, stringResource(R.string.gallery_search_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) viewModel.clearSelection()
                        else if (pager.currentPage == 3 && activeAlbum != null) activeAlbum = null
                        else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { moveTargets = selectedItems.toList() }) {
                            Icon(Icons.Default.Folder, stringResource(R.string.album_move))
                        }
                        IconButton(
                            enabled = videoActionsEnabled && exportProgress == null,
                            onClick = { folderLauncher.launch(null) }
                        ) {
                            Icon(Icons.Default.SaveAlt, stringResource(R.string.content_description_export_selected))
                        }
                        IconButton(
                            enabled = videoActionsEnabled && compression !is CompressionState.Running,
                            onClick = { viewModel.openCompress(items.filterIsInstance<GalleryItem.VideoItem>().filter { it.identity in selectedItems }.map { it.video }) }
                        ) {
                            Icon(Icons.Default.Compress, stringResource(R.string.content_description_compress))
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.content_description_delete_selected))
                        }
                    } else {
                        MediaFilterButton(filter, viewModel::setFilter)
                        IconButton(
                            enabled = compression !is CompressionState.Running && items.any { it is GalleryItem.VideoItem },
                            onClick = { viewModel.openCompress(items.filterIsInstance<GalleryItem.VideoItem>().map { it.video }) }
                        ) { Icon(Icons.Default.Compress, stringResource(R.string.media_compress_visible)) }
                        LayoutMenu(
                            current = layout,
                            gridColumns = gridColumns,
                            onColumns = {
                                gridColumns = it
                                GalleryLayoutStore.saveGridColumns(prefs, it)
                            },
                            onPick = {
                                layout = it
                                GalleryLayoutStore.save(prefs, it)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pager.currentPage) {
                listOf(R.string.media_all, R.string.media_videos, R.string.media_photos, R.string.album_title).forEachIndexed { index, title ->
                    Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(stringResource(title)) })
                }
            }
            if (pager.currentPage == 3 && activeAlbum != null) {
                TextButton(onClick = { activeAlbum = null }) {
                    Text(if (activeAlbum == 0L) stringResource(R.string.album_unassigned) else albums.firstOrNull { it.id == activeAlbum }?.name.orEmpty())
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val pageItems = remember(allItems, page, activeAlbum) { itemsForPage(allItems, page, activeAlbum) }
            if (page == 3 && activeAlbum == null) {
                AlbumBrowser(albums, allItems, { activeAlbum = it }, { viewModel.createAlbum(it) }, viewModel::renameAlbum, viewModel::deleteAlbum)
            } else if (pageItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (query.isEmpty()) R.string.empty_gallery else R.string.gallery_search_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GallerySizing.columns(layout, gridColumns)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (layout == GalleryLayout.List) 8.dp else 12.dp)
                ) {
                    items(pageItems, key = { it.identity.stableKey }) { item ->
                        when (item) {
                            is GalleryItem.VideoItem -> {
                                val video = item.video
                                GalleryItemCard(
                                    video = video,
                                    layout = layout,
                                    isSelected = item.identity in selectedItems,
                                    onVideoClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleSelection(item.identity)
                                        } else {
                                            viewModel.playVideo(video)
                                            navController.popBackStack()
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelection(item.identity) },
                                    onRenameClick = { videoToRename = video },
                                    onChangeThumbnailClick = {
                                        videoForThumbnail = video
                                        thumbnailLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    hasNote = video.id in videoNotes,
                                    onNoteClick = { videoForNote = video },
                                    onCompressClick = { viewModel.openCompress(listOf(video)) },
                                    onMoveClick = { moveTargets = listOf(item.identity) },
                                    onDeleteClick = { viewModel.deleteItem(item) }
                                )
                            }
                            is GalleryItem.PhotoItem -> PhotoItemCard(
                                photo = item.photo,
                                layout = layout,
                                isSelected = item.identity in selectedItems,
                                onClick = {
                                    if (isSelectionMode) viewModel.toggleSelection(item.identity)
                                    else viewedPhotoId = item.photo.id
                                },
                                onLongClick = { viewModel.toggleSelection(item.identity) },
                                onRename = { photoToRename = item.photo },
                                onMove = { moveTargets = listOf(item.identity) },
                                onDelete = { viewModel.deleteItem(item) }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

private fun itemsForPage(items: List<GalleryItem>, page: Int, albumId: Long?): List<GalleryItem> = when (page) {
    1 -> items.filterIsInstance<GalleryItem.VideoItem>()
    2 -> items.filterIsInstance<GalleryItem.PhotoItem>()
    3 -> if (albumId == null) emptyList() else items.filter { it.albumId == albumId.takeUnless { id -> id == 0L } }
    else -> items
}

/** Layouts and a separately persisted grid density, including while One/List are active. */
@Composable
private fun LayoutMenu(
    current: GalleryLayout,
    gridColumns: Int,
    onColumns: (Int) -> Unit,
    onPick: (GalleryLayout) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(current.icon(), stringResource(R.string.content_description_gallery_layout))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in GalleryLayout.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                    leadingIcon = { Icon(option.icon(), null) },
                    trailingIcon = { if (option == current) Icon(Icons.Default.Check, null) }
                )
            }
            HorizontalDivider()
            Column(Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.gallery_grid_columns, gridColumns))
                Slider(
                    value = gridColumns.toFloat(),
                    onValueChange = { onColumns(GallerySizing.gridColumns(it)) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
        }
    }
}

private fun GalleryLayout.icon() = when (this) {
    GalleryLayout.Large -> Icons.Default.ViewColumn
    GalleryLayout.Two -> Icons.Default.GridView
    GalleryLayout.One -> Icons.Default.ViewAgenda
    GalleryLayout.List -> Icons.Default.ViewList
}

private fun GalleryLayout.label() = when (this) {
    GalleryLayout.Large -> R.string.gallery_layout_large
    GalleryLayout.Two -> R.string.gallery_layout_two
    GalleryLayout.One -> R.string.gallery_layout_one
    GalleryLayout.List -> R.string.gallery_layout_list
}