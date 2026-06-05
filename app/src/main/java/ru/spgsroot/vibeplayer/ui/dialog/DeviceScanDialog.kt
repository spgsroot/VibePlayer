package ru.spgsroot.vibeplayer.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.spgsroot.buttplug.compose.DeviceScanner
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
    val client = connectionManager.buttplugClient
    val deviceState by connectionManager.state.collectAsStateWithLifecycle()

    // Track when we go from connected → disconnected to fire onDisconnect
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(deviceState) {
        if (deviceState is DeviceState.Connected || deviceState is DeviceState.Scanning) {
            wasConnected = true
        }
        if (wasConnected && deviceState is DeviceState.Disconnected) {
            wasConnected = false
            onDisconnect()
        }
    }

    if (client != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_device_connect)) },
            text = {
                DeviceScanner(
                    client = client,
                    onDeviceSelected = { device -> onDeviceSelected(device.index) },
                    showUrlInput = true
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }
}
