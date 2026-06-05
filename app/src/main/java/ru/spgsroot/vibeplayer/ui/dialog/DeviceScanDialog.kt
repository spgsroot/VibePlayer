package ru.spgsroot.vibeplayer.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugConnectionManager
import ru.spgsroot.vibeplayer.device.buttplug.DeviceState

@Composable
fun DeviceScanDialog(
    connectionManager: ButtplugConnectionManager,
    activeDeviceIndex: Int?,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onDeviceSelected: (Int) -> Unit
) {
    val deviceState by connectionManager.state.collectAsStateWithLifecycle()
    val devices by connectionManager.devices.collectAsStateWithLifecycle()
    var serverUrl by remember { mutableStateOf(TextFieldValue(ButtplugConnectionManager.DEFAULT_URL)) }
    val canEditServerUrl = deviceState is DeviceState.Disconnected || deviceState is DeviceState.Error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_device_connect)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.device_connect_hint),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.label_server_url)) },
                    placeholder = { Text(stringResource(R.string.device_connect_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEditServerUrl
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (deviceState) {
                    is DeviceState.Scanning -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.searching_devices))
                        }
                    }
                    is DeviceState.Disconnected -> ConnectButton(
                        onClick = { connectionManager.connect(serverUrl.text) }
                    )
                    is DeviceState.Connected -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.BluetoothConnected,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50)
                            )
                            Text(
                                text = stringResource(
                                    R.string.device_status_connected,
                                    (deviceState as DeviceState.Connected).deviceName
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_device_disconnect))
                            }
                            Button(
                                onClick = { connectionManager.startScanning() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_rescan))
                            }
                        }
                    }
                    is DeviceState.Error -> {
                        Text(
                            (deviceState as DeviceState.Error).reason,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectButton(
                            onClick = { connectionManager.connect(serverUrl.text) }
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (devices.isNotEmpty()) {
                    Text(stringResource(R.string.found_devices), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    devices.forEach { device ->
                        val isSelected = activeDeviceIndex == device.index
                        ListItem(
                            headlineContent = { Text(device.name) },
                            supportingContent = if (isSelected) {
                                { Text(stringResource(R.string.device_selected)) }
                            } else {
                                null
                            },
                            leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                            trailingContent = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = stringResource(R.string.device_selected),
                                        tint = Color(0xFF4CAF50)
                                    )
                                }
                            } else {
                                null
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device.index)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
private fun ConnectButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.btn_start_scan))
    }
}
