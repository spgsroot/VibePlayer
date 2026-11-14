package ru.spgsroot.peachy.ui.player

import android.media.AudioManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import kotlin.math.abs
import kotlin.math.roundToInt
import ru.spgsroot.peachy.playback.player.ExoPlayerWrapper
import androidx.compose.ui.res.stringResource
import ru.spgsroot.peachy.R

private enum class Drag { None, Seek, Brightness, Volume, Pan, Pinch }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun Modifier.playerGestures(
    player: ExoPlayerWrapper, zoom: Float, onZoom: (Float) -> Unit, onPan: (Offset) -> Unit,
    onSeek: (Long) -> Unit, onInteraction: () -> Unit, onFeedback: (String) -> Unit
): Modifier {
    val activity = LocalActivity.current
    val audio = LocalContext.current.getSystemService(AudioManager::class.java)
    val brightnessLabel = stringResource(R.string.media_brightness)
    val volumeLabel = stringResource(R.string.media_volume)
    val latestZoom by rememberUpdatedState(zoom)
    val latestPan by rememberUpdatedState(onPan)
    val latestScale by rememberUpdatedState(onZoom)
    val latestSeek by rememberUpdatedState(onSeek)
    DisposableEffect(activity) {
        val original = activity?.window?.attributes?.screenBrightness
        onDispose { if (original != null && activity != null) activity.window.attributes = activity.window.attributes.apply { screenBrightness = original } }
    }
    return pointerInput(player, activity) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var drag = Drag.None
            var total = Offset.Zero
            val startPosition = player.getCurrentPosition().coerceAtLeast(0)
            val duration = player.getPlayer().duration.coerceAtLeast(0)
            var seek = startPosition
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val startBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.any { it.isConsumed } && drag == Drag.None) break
                if (event.changes.count { it.pressed } > 1) {
                    drag = Drag.Pinch
                    latestScale(event.calculateZoom())
                    latestPan(event.calculatePan())
                    event.changes.forEach { it.consume() }
                    onInteraction()
                    continue
                }
                val delta = change.positionChange()
                total += delta
                if (drag == Drag.None && total.getDistance() > viewConfiguration.touchSlop) {
                    drag = when {
                        latestZoom > 1.01f -> Drag.Pan
                        abs(total.x) > abs(total.y) -> Drag.Seek
                        down.position.x < size.width * .35f -> Drag.Brightness
                        down.position.x > size.width * .65f -> Drag.Volume
                        else -> Drag.None
                    }
                }
                when (drag) {
                    Drag.Seek -> {
                        seek = (startPosition + total.x / size.width * duration).toLong().coerceIn(0, duration)
                        onFeedback("${seek / 60000}:${(seek / 1000 % 60).toString().padStart(2, '0')}")
                    }
                    Drag.Brightness -> {
                        val value = (startBrightness - total.y / size.height).coerceIn(.01f, 1f)
                        activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = value } }
                        onFeedback("$brightnessLabel ${(value * 100).roundToInt()}%")
                    }
                    Drag.Volume -> {
                        val value = (startVolume - total.y / size.height * maxVolume).roundToInt().coerceIn(0, maxVolume)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                        onFeedback("$volumeLabel ${(value * 100f / maxVolume.coerceAtLeast(1)).roundToInt()}%")
                    }
                    Drag.Pan -> latestPan(delta)
                    else -> Unit
                }
                if (drag != Drag.None) { change.consume(); onInteraction() }
            } while (event.changes.any { it.pressed })
            if (drag == Drag.Seek) latestSeek(seek)
        }
    }
}
