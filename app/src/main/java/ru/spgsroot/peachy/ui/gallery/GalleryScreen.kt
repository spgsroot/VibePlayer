package ru.spgsroot.peachy.ui.gallery

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.domain.model.Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val videos by viewModel.videos.collectAsState()
    val selectedVideoIds by viewModel.selectedVideoIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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
    val context = LocalContext.current

    var videoToRename by remember { mutableStateOf<Video?>(null) }
    var videoForThumbnail by remember { mutableStateOf<Video?>(null) }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val progress = exportProgress
                    if (progress != null) {
                        Text(stringResource(R.string.gallery_exporting, progress.first, progress.second))
                    } else if (isSelectionMode) {
                        Text(stringResource(R.string.gallery_selected, selectedVideoIds.size))
                    } else {
                        Text(stringResource(R.string.gallery_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) viewModel.clearSelection()
                        else navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            enabled = exportProgress == null,
                            onClick = { folderLauncher.launch(null) }
                        ) {
                            Icon(Icons.Default.SaveAlt, stringResource(R.string.content_description_export_selected))
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.content_description_delete_selected))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_gallery),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                GalleryItemCard(
                    video = video,
                    isSelected = selectedVideoIds.contains(video.id),
                    onVideoClick = {
                        if (isSelectionMode) {
                            viewModel.toggleSelection(video.id)
                        } else {
                            viewModel.playVideo(video)
                            navController.popBackStack()
                        }
                    },
                    onLongClick = { viewModel.toggleSelection(video.id) },
                    onRenameClick = { videoToRename = video },
                    onChangeThumbnailClick = {
                        videoForThumbnail = video
                        thumbnailLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onDeleteClick = {
                        viewModel.clearSelection()
                        viewModel.toggleSelection(video.id)
                        viewModel.deleteSelected()
                    }
                )
            }
        }
        }
    }
}