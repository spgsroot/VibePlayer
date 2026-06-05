package io.github.spgsroot.buttplug.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.spgsroot.buttplug.device.ActuatorType
import io.github.spgsroot.buttplug.device.ButtplugDevice
import io.github.spgsroot.buttplug.device.DeviceFeature
import io.github.spgsroot.buttplug.device.SensorType

/**
 * A card displaying device information with feature badges.
 *
 * Shows the device display name, internal name, feature counts
 * (vibrators, other actuators, sensors), and a selection indicator
 * when [isSelected] is true.
 *
 * @param device The device to display.
 * @param isSelected Whether this device is the currently selected one.
 * @param onClick Called when the card is tapped.
 * @param modifier Optional modifier for the card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceCard(
    device: ButtplugDevice,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val vibrateCount = device.vibrateFeatures().size
    val otherActuatorCount = device.features.count { feature ->
        feature.outputTypes.any { it != ActuatorType.Vibrate }
    }
    val sensorCount = device.features.count { it.inputTypes.isNotEmpty() }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "cardColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, borderColor, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName.ifBlank { device.name },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (device.displayName.isNotBlank() && device.name != device.displayName) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Feature badges
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (vibrateCount > 0) {
                        FeatureBadge(
                            icon = Icons.Default.Vibration,
                            text = if (vibrateCount == 1) "Vibrate" else "$vibrateCount vibrators",
                            color = Color(0xFFE91E63)
                        )
                    }
                    if (otherActuatorCount > 0) {
                        FeatureBadge(
                            icon = Icons.Default.Sensors,
                            text = "$otherActuatorCount actuator${if (otherActuatorCount > 1) "s" else ""}",
                            color = Color(0xFFFF9800)
                        )
                    }
                    if (sensorCount > 0) {
                        FeatureBadge(
                            icon = Icons.Default.Sensors,
                            text = "$sensorCount sensor${if (sensorCount > 1) "s" else ""}",
                            color = Color(0xFF2196F3)
                        )
                    }
                }
            }

            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * A small chip badge showing a device feature type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun DeviceCardPreview() {
    val device = ButtplugDevice(
        index = 0,
        name = "Lovense Hush",
        displayName = "Hush 2",
        features = listOf(
            DeviceFeature(index = 0, description = "Vibrator", outputTypes = listOf(ActuatorType.Vibrate)),
            DeviceFeature(index = 1, description = "Rotator", outputTypes = listOf(ActuatorType.Rotate)),
            DeviceFeature(
                index = 2,
                description = "Battery sensor",
                inputTypes = listOf(SensorType.Battery)
            )
        )
    )

    MaterialTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DeviceCard(device = device)
            DeviceCard(device = device, isSelected = true)
        }
    }
}
