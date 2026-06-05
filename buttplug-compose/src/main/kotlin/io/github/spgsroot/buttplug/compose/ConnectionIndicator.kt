package io.github.spgsroot.buttplug.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.spgsroot.buttplug.ButtplugClientState

/**
 * A compact connection status badge showing a colored indicator dot
 * and a human-readable state label.
 *
 * Colors:
 * - Green  → Connected / Scanning
 * - Yellow → Connecting / Handshaking / Reconnecting
 * - Red    → Error
 * - Gray   → Disconnected
 *
 * The dot pulses gently when in a transitional state (Connecting, Handshaking, Scanning, Reconnecting).
 */
@Composable
fun ConnectionIndicator(
    state: ButtplugClientState,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = state.toStatusColor(),
        animationSpec = tween(400),
        label = "dotColor"
    )

    val isTransitional = state is ButtplugClientState.Connecting ||
            state is ButtplugClientState.Handshaking ||
            state is ButtplugClientState.Scanning ||
            state is ButtplugClientState.Reconnecting

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    // Pulse animation for transitional states; static 1.0 for stable states.
    // Always run the infinite transition to avoid conditional composable calls,
    // but multiply by 1.0 when not transitional for no visible effect.
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val effectiveAlpha = if (isTransitional) pulseAlpha else 1.0f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = effectiveAlpha))
        )

        Text(
            text = state.toLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Maps a [ButtplugClientState] to a human-readable label.
 */
fun ButtplugClientState.toLabel(): String = when (this) {
    is ButtplugClientState.Disconnected -> "Disconnected"
    is ButtplugClientState.Connecting -> "Connecting…"
    is ButtplugClientState.Handshaking -> "Handshaking…"
    is ButtplugClientState.Connected -> "Connected"
    is ButtplugClientState.Scanning -> "Scanning…"
    is ButtplugClientState.Reconnecting -> "Reconnecting (${attempt}/${maxAttempts})"
    is ButtplugClientState.Error -> "Error"
}

/**
 * Maps a [ButtplugClientState] to a status indicator color.
 */
fun ButtplugClientState.toStatusColor(): Color = when (this) {
    is ButtplugClientState.Disconnected -> Color(0xFF9E9E9E) // Gray
    is ButtplugClientState.Connecting,
    is ButtplugClientState.Handshaking,
    is ButtplugClientState.Reconnecting -> Color(0xFFFFC107) // Amber
    is ButtplugClientState.Scanning -> Color(0xFF2196F3) // Blue
    is ButtplugClientState.Connected -> Color(0xFF4CAF50) // Green
    is ButtplugClientState.Error -> Color(0xFFF44336) // Red
}

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun ConnectionIndicatorDisconnectedPreview() {
    MaterialTheme {
        ConnectionIndicator(state = ButtplugClientState.Disconnected)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun ConnectionIndicatorConnectedPreview() {
    MaterialTheme {
        ConnectionIndicator(
            state = ButtplugClientState.Connected(
                serverName = "Intiface",
                protocolVersionMajor = 4,
                protocolVersionMinor = 0,
                maxPingTime = 10000
            )
        )
    }
}
