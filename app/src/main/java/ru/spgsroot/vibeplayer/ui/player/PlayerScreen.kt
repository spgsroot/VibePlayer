package ru.spgsroot.vibeplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.playback.player.PlayerState
import ru.spgsroot.vibeplayer.ui.dialog.AddVideoDialog
import ru.spgsroot.vibeplayer.ui.dialog.BatchImportDialog
import ru.spgsroot.vibeplayer.ui.dialog.UrlInputDialog
import ru.spgsroot.vibeplayer.ui.settings.SettingsDrawer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }

    // Состояние полноэкранного режима
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Состояние зума (сохраняется при переключении между видео благодаря rememberSaveable)
    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }

    LaunchedEffect(isFullscreen) {
        if (activity != null) {
            val window = activity.window
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                // Ориентация экрана зависит от положения телефона (переворот работает на все 360 градусов)
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Если мы в полноэкранном режиме, нажатие назад просто вернет нас в обычный режим
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.importVideoFromUri(it) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { SettingsDrawer() }
    ) {
        Scaffold(
            topBar = {
                // Скрываем TopAppBar в полноэкранном режиме
                if (!isFullscreen) {
                    TopAppBar(
                        title = { Text("VibePlayer") },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Default.Menu, "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate("gallery") }) {
                                Icon(Icons.Default.VideoLibrary, "Gallery")
                            }
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, "Add video")
                            }
                        }
                    )
                }
            }
        ) { padding ->
            when (uiState) {
                PlayerUiState.Empty -> EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onAddVideoClick = { showAddDialog = true }
                )
                PlayerUiState.Ready -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                ) {
                    PlayerContent(
                        modifier = Modifier.fillMaxSize(),
                        playerState = playerState,
                        exoPlayerWrapper = viewModel.exoPlayerWrapper,
                        isFullscreen = isFullscreen,
                        onFullscreenToggle = { isFullscreen = !isFullscreen },
                        onPlayPause = viewModel::onPlayPause,
                        onNext = viewModel::onNext,
                        onPrevious = viewModel::onPrevious,
                        onSeek = viewModel::onSeek,
                        zoomScale = zoomScale,
                        panX = panX,
                        panY = panY,
                        onZoomChange = { zoomMultiplier ->
                            zoomScale = (zoomScale * zoomMultiplier).coerceIn(1f, 4f)
                        },
                        onPanChange = { offset ->
                            panX += offset.x
                            panY += offset.y
                            // Сбрасываем сдвиг, если вернулись к оригинальному размеру
                            if (zoomScale <= 1.01f) {
                                panX = 0f
                                panY = 0f
                            }
                        },
                        onResetZoom = {
                            zoomScale = 1f
                            panX = 0f
                            panY = 0f
                        }
                    )

                    if (!isFullscreen) {
                        // TopAppBar padding overlay, чтобы видео не уходило под AppBar когда он виден
                        Spacer(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(padding.calculateTopPadding())
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddVideoDialog(
            onDismiss = { showAddDialog = false },
            onGalleryClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onUrlClick = { showUrlDialog = true },
            onBatchClick = { showBatchDialog = true }
        )
    }

    if (showUrlDialog) {
        UrlInputDialog(
            onDismiss = { showUrlDialog = false },
            onDownload = { url ->
                viewModel.importVideoFromUrl(url)
            }
        )
    }

    if (showBatchDialog) {
        BatchImportDialog(
            onDismiss = { showBatchDialog = false },
            onImport = { urls, progressCallback ->
                viewModel.importVideosFromUrls(urls, progressCallback)
            }
        )
    }
}