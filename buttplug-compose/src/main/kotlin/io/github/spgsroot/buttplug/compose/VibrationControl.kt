package io.github.spgsroot.buttplug.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.spgsroot.buttplug.ButtplugClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A vibration intensity slider that sends commands to a device in real-time.
 *
 * The slider ranges from 0 to 100 (mapped to 0.0–1.0 speed).
 * Changes are debounced by ~50ms to avoid flooding the server.
 * When the slider reaches 0%, a stop command is sent.
 *
 * @param client The connected [ButtplugClient].
 * @param deviceIndex The index of the device to control.
 * @param featureIndex The feature index for the vibrator (default 0).
 * @param modifier Optional modifier.
 * @param enabled Whether the control is interactive.
 */
@Composable
fun VibrationControl(
    client: ButtplugClient,
    deviceIndex: Int,
    featureIndex: Int = 0,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isSending by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val trackColor by animateColorAsState(
        targetValue = if (enabled)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "trackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (sliderValue > 0f)
            Color(0xFFE91E63) // Vibration pink
        else
            MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "thumbColor"
    )

    val intensityPercent = (sliderValue * 100).toInt()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (sliderValue > 0f) thumbColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vibration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Intensity percentage badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (sliderValue > 0f) thumbColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$intensityPercent%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (sliderValue > 0f) thumbColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Slider
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                    debounceJob?.cancel()
                    debounceJob = scope.launch {
                        delay(50L) // debounce
                        isSending = true
                        try {
                            val speed = newValue.toDouble()
                            if (speed <= 0.01) {
                                client.sendStop(deviceIndex, featureIndex)
                            } else {
                                client.sendVibrate(deviceIndex, featureIndex, speed)
                            }
                        } catch (_: Exception) {
                            // Silently handle — caller can observe state
                        } finally {
                            isSending = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                valueRange = 0f..1f,
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,
                    activeTrackColor = trackColor,
                    inactiveTrackColor = trackColor.copy(alpha = 0.15f)
                ),
                enabled = enabled
            )

            // Quick-action buttons
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stop button
                FilledIconButton(
                    onClick = {
                        sliderValue = 0f
                        scope.launch {
                            try {
                                client.sendStop(deviceIndex, featureIndex)
                            } catch (_: Exception) { }
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = enabled,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop vibration",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Quick presets
                listOf(25f, 50f, 75f, 100f).forEach { preset ->
                    val presetValue = preset / 100f
                    FilledTonalButton(
                        onClick = {
                            sliderValue = presetValue
                            scope.launch {
                                try {
                                    client.sendVibrate(deviceIndex, featureIndex, presetValue.toDouble())
                                } catch (_: Exception) { }
                            }
                        },
                        modifier = Modifier.height(36.dp).weight(1f),
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "${preset.toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun VibrationControlPreview() {
    MaterialTheme {
        // Simulated preview layout
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vibration", style = MaterialTheme.typography.titleSmall)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE91E63).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("65%", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE91E63))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = 0.65f,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE91E63),
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconButton(
                        onClick = {},
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                    }
                    listOf("25%", "50%", "75%", "100%").forEach {
                        FilledTonalButton(
                            onClick = {},
                            modifier = Modifier.height(36.dp).weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(it, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
