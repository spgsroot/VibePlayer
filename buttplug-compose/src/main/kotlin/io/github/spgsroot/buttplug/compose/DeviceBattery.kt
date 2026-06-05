package io.github.spgsroot.buttplug.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.spgsroot.buttplug.ButtplugClient
import io.github.spgsroot.buttplug.ButtplugException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A component that reads and displays the battery level of a device feature.
 *
 * Reads the battery on first composition and then polls every 30 seconds.
 * Provides a manual refresh button.
 *
 * @param client The connected [ButtplugClient].
 * @param deviceIndex The index of the device to query.
 * @param featureIndex The feature index for the battery sensor (default 0).
 * @param modifier Optional modifier.
 */
@Composable
fun DeviceBattery(
    client: ButtplugClient,
    deviceIndex: Int,
    featureIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isReading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val refresh: suspend () -> Unit = {
        isReading = true
        errorMessage = null
        try {
            batteryLevel = client.readBattery(deviceIndex, featureIndex)
        } catch (e: ButtplugException) {
            errorMessage = e.message ?: "Read failed"
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
        } finally {
            isReading = false
        }
    }

    // Initial read + periodic polling every 30s
    LaunchedEffect(client, deviceIndex, featureIndex) {
        refresh()
        while (isActive) {
            delay(30_000L)
            if (isActive) refresh()
        }
    }

    val level = batteryLevel.coerceIn(0, 100)
    val progress = level / 100f

    val barColor by animateColorAsState(
        targetValue = when {
            level < 0 -> MaterialTheme.colorScheme.outlineVariant
            level <= 15 -> Color(0xFFF44336) // Red
            level <= 30 -> Color(0xFFFF9800) // Orange
            level <= 60 -> Color(0xFFFFC107) // Amber
            else -> Color(0xFF4CAF50) // Green
        },
        animationSpec = tween(500),
        label = "barColor"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Battery icon
            Icon(
                imageVector = batteryIconForLevel(level),
                contentDescription = "Battery level",
                modifier = Modifier.size(36.dp),
                tint = barColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Progress bar + percentage
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (level >= 0) "$level%" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isReading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reading…",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { if (level >= 0) progress else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Refresh button
            IconButton(
                onClick = { scope.launch { refresh() } },
                enabled = !isReading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh battery",
                    modifier = Modifier.size(20.dp),
                    tint = if (isReading)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Selects the appropriate Material icon for a given battery level.
 */
private fun batteryIconForLevel(level: Int): ImageVector = when {
    level < 0 -> Icons.Default.Battery0Bar // unknown
    level >= 95 -> Icons.Default.BatteryFull
    level >= 80 -> Icons.Default.Battery6Bar
    level >= 65 -> Icons.Default.Battery5Bar
    level >= 50 -> Icons.Default.Battery4Bar
    level >= 35 -> Icons.Default.Battery3Bar
    level >= 20 -> Icons.Default.Battery2Bar
    level >= 10 -> Icons.Default.Battery1Bar
    else -> Icons.Default.Battery0Bar
}

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun DeviceBatteryHighPreview() {
    MaterialTheme {
        // Simulated preview — would need a real client in practice
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("87%", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { 0.87f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

// fillMaxWidth() is provided by Compose foundation layout, no extension needed.
