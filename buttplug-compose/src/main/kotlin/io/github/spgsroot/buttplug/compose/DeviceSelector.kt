package io.github.spgsroot.buttplug.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.spgsroot.buttplug.device.ActuatorType
import io.github.spgsroot.buttplug.device.ButtplugDevice
import io.github.spgsroot.buttplug.device.DeviceFeature

/**
 * A dropdown selector for choosing a device from the connected device list.
 *
 * Devices can be filtered by [filterActuator] type. Only devices that support
 * the specified actuator type appear in the list. If [filterActuator] is null,
 * all devices are shown.
 *
 * @param devices The list of available devices.
 * @param selectedIndex The currently selected device index, or null for none.
 * @param onDeviceSelected Called with the device index when selected.
 * @param modifier Optional modifier.
 * @param filterActuator Actuator type to filter by. Default [ActuatorType.Vibrate].
 */
@Composable
fun DeviceSelector(
    devices: List<ButtplugDevice>,
    selectedIndex: Int? = null,
    onDeviceSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    filterActuator: ActuatorType? = ActuatorType.Vibrate
) {
    var expanded by remember { mutableStateOf(false) }

    val filteredDevices = if (filterActuator != null) {
        devices.filter { it.hasActuator(filterActuator) }
    } else {
        devices
    }

    val selectedDevice = filteredDevices.firstOrNull { it.index == selectedIndex }

    Column(modifier = modifier) {
        // Label
        Text(
            text = "Active Device",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Dropdown trigger
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = filteredDevices.isNotEmpty()) { expanded = true },
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (filterActuator == ActuatorType.Vibrate)
                        Icons.Default.Vibration
                    else
                        Icons.Default.Devices,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selectedDevice != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (selectedDevice != null) {
                        Text(
                            text = selectedDevice.displayName.ifBlank { selectedDevice.name },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedDevice.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = if (filteredDevices.isEmpty()) "No compatible devices"
                            else "Select a device…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand device list",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dropdown menu
            DropdownMenu(
                expanded = expanded && filteredDevices.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                filteredDevices.forEach { device ->
                    val isSelected = device.index == selectedIndex
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = device.displayName.ifBlank { device.name },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Feature count
                                val featureCount = if (filterActuator != null) {
                                    device.features.count { it.outputTypes.contains(filterActuator) }
                                } else {
                                    device.features.size
                                }
                                Text(
                                    text = "$featureCount ${filterActuator?.name?.lowercase() ?: "feature"}${if (featureCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onDeviceSelected(device.index)
                            expanded = false
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
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
private fun DeviceSelectorPreview() {
    val devices = listOf(
        ButtplugDevice(
            index = 0,
            name = "Lovense Hush 2",
            displayName = "Hush 2",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    description = "Vibrator",
                    outputTypes = listOf(ActuatorType.Vibrate)
                )
            )
        ),
        ButtplugDevice(
            index = 1,
            name = "Lovense Edge 2",
            displayName = "Edge 2",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    description = "Vibrator #1",
                    outputTypes = listOf(ActuatorType.Vibrate)
                ),
                DeviceFeature(
                    index = 1,
                    description = "Vibrator #2",
                    outputTypes = listOf(ActuatorType.Vibrate)
                )
            )
        ),
        ButtplugDevice(
            index = 2,
            name = "WeVibe Vector",
            displayName = "Vector",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    description = "Rotator",
                    outputTypes = listOf(ActuatorType.Rotate)
                )
            )
        )
    )

    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DeviceSelector(
                devices = devices,
                selectedIndex = 0
            )
        }
    }
}
