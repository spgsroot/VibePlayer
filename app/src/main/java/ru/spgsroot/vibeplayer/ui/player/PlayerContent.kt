package ru.spgsroot.vibeplayer.ui.player

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import ru.spgsroot.vibeplayer.playback.player.ExoPlayerWrapper
import ru.spgsroot.vibeplayer.playback.player.PlayerState

@OptIn(UnstableApi::class)
@Composable
fun PlayerContent(
    modifier: Modifier = Modifier,
    playerState: PlayerState,
    exoPlayerWrapper: ExoPlayerWrapper,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    zoomScale: Float,
    panX: Float,
    panY: Float,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    onResetZoom: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Layer 0: PlayerView (fullscreen video)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayerWrapper.getPlayer()
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            player?.play()
                        }
                        override fun onViewDetachedFromWindow(v: View) {}
                    })
                }
            },
            update = { playerView ->
                playerView.player = exoPlayerWrapper.getPlayer()
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panX,
                    translationY = panY
                )
        )

        // Layer 1: Controls Overlay (on top of video, processes zooming and taps)
        ControlsOverlay(
            playerState = playerState,
            exoPlayerWrapper = exoPlayerWrapper,
            isFullscreen = isFullscreen,
            onFullscreenToggle = onFullscreenToggle,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onZoomChange = onZoomChange,
            onPanChange = onPanChange,
            onResetZoom = onResetZoom
        )
    }
}