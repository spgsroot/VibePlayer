package ru.spgsroot.vibeplayer.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugConnectionManager
import ru.spgsroot.vibeplayer.device.buttplug.DeviceState

@Composable
fun DeviceScanDialog(
    connectionManager: ButtplugConnectionManager,
    onDismiss: () -> Unit,
    onDeviceSelected: (Int) -> Unit
) {
    val deviceState by connectionManager.state.collectAsStateWithLifecycle()
    val devices by connectionManager.devices.collectAsStateWithLifecycle()

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
                    is DeviceState.Disconnected -> {
                        Button(
                            onClick = { connectionManager.connect() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_start_scan))
                        }
                    }
                    is DeviceState.Error -> {
                        Text(
                            (deviceState as DeviceState.Error).reason,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (devices.isNotEmpty()) {
                    Text(stringResource(R.string.found_devices), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    devices.forEach { device ->
                        ListItem(
                            headlineContent = { Text(device.name) },
                            leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDeviceSelected(device.index)
                                    onDismiss()
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
