package ru.spgsroot.vibeplayer.ui.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugConnectionManager
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugDevice
import ru.spgsroot.vibeplayer.device.buttplug.DeviceState

@Composable
fun DeviceScanDialog(
    connectionManager: ButtplugConnectionManager,
    activeDeviceIndex: Int?,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onDeviceSelected: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val deviceState by connectionManager.state.collectAsStateWithLifecycle()
    val devices by connectionManager.devices.collectAsStateWithLifecycle()

    var serverUrl by remember { mutableStateOf(ButtplugConnectionManager.DEFAULT_URL) }
    var isConnecting by remember { mutableStateOf(false) }

    val isConnected = deviceState is DeviceState.Connected || deviceState is DeviceState.Scanning
    val isScanning = deviceState is DeviceState.Scanning
    val isError = deviceState is DeviceState.Error
    val canEditUrl = deviceState is DeviceState.Disconnected || deviceState is DeviceState.Error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_device_connect)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // URL Input
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.label_server_url)) },
                    placeholder = { Text("ws://192.168.1.100:12345") },
                    singleLine = true,
                    enabled = canEditUrl,
                    leadingIcon = {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Link,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (canEditUrl && serverUrl.isNotEmpty()) {
                            IconButton(onClick = { serverUrl = "" }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Connect / Disconnect row
                if (isConnected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    onDisconnect()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_device_disconnect))
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                connectionManager.startScanning()
                            },
                            enabled = deviceState !is DeviceState.Scanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_rescan))
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            isConnecting = true
                            connectionManager.connect(serverUrl.trim())
                        },
                        enabled = serverUrl.isNotBlank() && !isConnecting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.Bluetooth, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_device_connect))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status
                when {
                    isConnecting && !isConnected && !isError -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.searching_devices),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    isScanning -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.searching_devices),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    isError -> {
                        val errorMsg = (deviceState as DeviceState.Error).reason
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                errorMsg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Device list
                AnimatedVisibility(visible = devices.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.found_devices),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        devices.forEach { device ->
                            val isSelected = device.index == activeDeviceIndex
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable(enabled = !isSelected) {
                                        onDeviceSelected(device.index)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = if (isSelected) 4.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.BluetoothConnected
                                        else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            device.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (device.capabilities.isNotEmpty()) {
                                            Text(
                                                device.capabilities.joinToString(", "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Reset connecting state when state changes
                LaunchedEffect(deviceState) {
                    if (isConnected || isError) {
                        isConnecting = false
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
