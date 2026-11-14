package ru.spgsroot.peachy.ui.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.device.buttplug.ButtplugDevice
import ru.spgsroot.peachy.device.buttplug.DeviceState
import ru.spgsroot.peachy.playback.player.PlayerState

@OptIn(UnstableApi::class)
@Composable
fun ControlsOverlay(
    modifier: Modifier = Modifier,
    playerState: PlayerState,
    exoPlayerWrapper: ru.spgsroot.peachy.playback.player.ExoPlayerWrapper,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    zoomScale: Float,
    onPanChange: (Offset) -> Unit,
    onResetZoom: () -> Unit,
    deviceState: DeviceState,
    devices: List<ButtplugDevice>,
    activeDeviceIndex: Int?
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var feedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedback) { if (feedback != null) { delay(1000); feedback = null } }

    fun showControls() {
        controlsVisible = true
        lastInteraction++
    }

    LaunchedEffect(lastInteraction, controlsVisible) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Обработчик жестов (тапы для отображения меню и масштабирование)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .playerGestures(exoPlayerWrapper, zoomScale, onZoomChange, onPanChange, onSeek, ::showControls) { feedback = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls() },
                    onDoubleTap = { point ->
                        showControls()
                        val duration = exoPlayerWrapper.getPlayer().duration.coerceAtLeast(0)
                        val target = (exoPlayerWrapper.getCurrentPosition() + if (point.x < size.width / 2) -10_000 else 10_000).coerceIn(0, duration)
                        onSeek(target)
                        feedback = if (point.x < size.width / 2) "−10 s" else "+10 s"
                    },
                    onLongPress = { onResetZoom() }
                )
            }
    ) {
        val compact = maxHeight < 240.dp
        feedback?.let {
            Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), color = Color.Black.copy(alpha = .8f), shape = MaterialTheme.shapes.small) {
                Text(it, Modifier.padding(8.dp), color = Color.White)
            }
        }
        // Playback controls in center
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(if (compact) Alignment.TopCenter else Alignment.Center)
        ) {
            PlaybackControls(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }

        // Bottom area: SeekBar, Fullscreen, and Device status bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeekBar(
                    exoPlayerWrapper = exoPlayerWrapper,
                    onSeek = onSeek,
                    onInteraction = { showControls() },
                    isFullscreen = isFullscreen,
                    onFullscreenToggle = {
                        onFullscreenToggle()
                        showControls()
                    }
                )
                if (!compact) DeviceStatusBar(
                    deviceState = deviceState,
                    devices = devices,
                    activeDeviceIndex = activeDeviceIndex
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeekBar(
    exoPlayerWrapper: ru.spgsroot.peachy.playback.player.ExoPlayerWrapper,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                position = exoPlayerWrapper.getCurrentPosition()
                duration = exoPlayerWrapper.getPlayer().duration
            }
            delay(100)
        }
    }

    val safeDuration = if (duration > 0) duration else 0L
    val progress = if (safeDuration > 0) (position.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) else 0f
    val dragProgress = if (safeDuration > 0) (dragPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) else 0f

    Column {
        Slider(
            value = if (isDragging) dragProgress else progress,
            onValueChange = {
                isDragging = true
                dragPosition = (it * safeDuration).toLong()
                onInteraction()
            },
            onValueChangeFinished = {
                if (isDragging) {
                    onSeek(dragPosition)
                    isDragging = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(if (isDragging) dragPosition else position),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = formatTime(safeDuration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = onFullscreenToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.SkipPrevious,
                "Previous",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = when (playerState) {
                    is PlayerState.Playing -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = "Play/Pause",
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.SkipNext,
                "Next",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun DeviceStatusBar(
    deviceState: DeviceState,
    devices: List<ButtplugDevice>,
    activeDeviceIndex: Int?
) {
    val activeDevice = activeDeviceIndex?.let { index -> devices.firstOrNull { it.index == index } }
    val firstDevice = devices.firstOrNull()
    val (statusText, trailingText, icon, iconColor) = when {
        activeDevice != null -> Quadruple(
            stringResource(R.string.device_status_active, activeDevice.name),
            stringResource(R.string.device_status_on),
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50)
        )
        deviceState is DeviceState.Connected && firstDevice != null -> Quadruple(
            stringResource(R.string.device_status_connected, firstDevice.name),
            stringResource(R.string.device_status_select),
            Icons.Default.BluetoothConnected,
            Color(0xFFFFC107)
        )
        deviceState is DeviceState.Scanning -> Quadruple(
            stringResource(R.string.searching_devices),
            "…",
            Icons.Default.Sync,
            Color(0xFF64B5F6)
        )
        deviceState is DeviceState.Error -> Quadruple(
            stringResource(R.string.device_status_error),
            "!",
            Icons.Default.Error,
            MaterialTheme.colorScheme.error
        )
        else -> Quadruple(
            stringResource(R.string.device_status_disconnected),
            stringResource(R.string.device_status_off),
            Icons.Default.BluetoothDisabled,
            Color.White
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    "Device",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.RadioButtonChecked,
                    "Status",
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    trailingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
