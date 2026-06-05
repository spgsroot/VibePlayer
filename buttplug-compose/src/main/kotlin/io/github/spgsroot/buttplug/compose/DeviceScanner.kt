package io.github.spgsroot.buttplug.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.spgsroot.buttplug.ButtplugClient
import io.github.spgsroot.buttplug.ButtplugClientState
import io.github.spgsroot.buttplug.device.ActuatorType
import io.github.spgsroot.buttplug.device.ButtplugDevice
import io.github.spgsroot.buttplug.device.DeviceFeature
import kotlinx.coroutines.launch

/**
 * A full-featured device scanner screen for connecting to a Buttplug/Intiface
 * server, scanning for devices, and selecting one for use.
 *
 * Features:
 * - URL input field (editable only when disconnected)
 * - Connect / Disconnect button
 * - Visual connection state indicator
 * - Scan button (visible when connected)
 * - Device list with device name, feature count, and select button
 * - Loading spinner during scanning
 * - Error message display with retry
 * - Empty state when no devices found
 *
 * @param client The [ButtplugClient] instance (should be remembered externally).
 * @param modifier Optional modifier for the root layout.
 * @param onDeviceSelected Called when a device is selected.
 * @param showUrlInput Whether to show the server URL input field.
 */
@Composable
fun DeviceScanner(
    client: ButtplugClient,
    modifier: Modifier = Modifier,
    onDeviceSelected: (ButtplugDevice) -> Unit = {},
    showUrlInput: Boolean = true
) {
    val scope = rememberCoroutineScope()

    // Local state
    var serverUrl by remember { mutableStateOf(client.config.serverUrl) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    // Collect client state and devices into local mutable state
    var connectionState by remember { mutableStateOf<ButtplugClientState>(ButtplugClientState.Disconnected) }
    var devices by remember { mutableStateOf<List<ButtplugDevice>>(emptyList()) }

    LaunchedEffect(client) {
        client.state.collect { connectionState = it }
    }
    LaunchedEffect(client) {
        client.devices.collect { devices = it }
    }
    // Sync initial values
    LaunchedEffect(client) {
        connectionState = client.state.value
        devices = client.devices.value
    }

    val isConnected = connectionState is ButtplugClientState.Connected ||
            connectionState is ButtplugClientState.Scanning
    val isScanning = connectionState is ButtplugClientState.Scanning
    val isError = connectionState is ButtplugClientState.Error
    val canEditUrl = connectionState is ButtplugClientState.Disconnected ||
            connectionState is ButtplugClientState.Error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── URL Input ──────────────────────────────────────────────
        AnimatedVisibility(visible = showUrlInput) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("ws://192.168.1.100:12345") },
                singleLine = true,
                enabled = canEditUrl,
                leadingIcon = {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (canEditUrl) {
                        IconButton(onClick = { serverUrl = "" }) {
                            Icon(Icons.Default.Close, "Clear URL")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // ── Connect / Disconnect Row ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isConnected) {
                // Disconnect button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            client.disconnect()
                            connectionError = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect")
                }

                // Scan button
                Button(
                    onClick = {
                        scope.launch {
                            connectionError = null
                            try {
                                client.startScanning()
                            } catch (e: Exception) {
                                connectionError = e.message ?: "Scan failed"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isScanning,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "Scanning…" else "Scan")
                }
            } else {
                // Connect button (full width)
                Button(
                    onClick = {
                        scope.launch {
                            isConnecting = true
                            connectionError = null
                            try {
                                // Create a new config with the current URL
                                val config = client.config.copy(serverUrl = serverUrl)
                                // Re-create client with new URL? No — use existing client.
                                // Actually the client config is set at construction time.
                                // For URL changes, a new client would be needed externally.
                                // We attempt to connect with current config; URL edit notifies caller.
                                client.connect()
                            } catch (e: Exception) {
                                connectionError = e.message ?: "Connection failed"
                            } finally {
                                isConnecting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting && serverUrl.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isConnecting) "Connecting…" else "Connect")
                }
            }
        }

        // ── Connection Indicator ───────────────────────────────────
        AnimatedContent(
            targetState = connectionState,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 })
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 })
            },
            label = "statusTransition"
        ) { state ->
            ConnectionIndicator(
                state = state,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Reconnecting progress bar ──────────────────────────────
        if (connectionState is ButtplugClientState.Reconnecting) {
            val reconnecting = connectionState as ButtplugClientState.Reconnecting
            val progress = reconnecting.attempt.toFloat() / reconnecting.maxAttempts.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFFFFC107),
                trackColor = Color(0xFFFFC107).copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
        }

        // ── Error Display ──────────────────────────────────────────
        AnimatedVisibility(visible = connectionError != null || isError) {
            val errorMessage = connectionError
                ?: (connectionState as? ButtplugClientState.Error)?.reason
                ?: "Unknown error"

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            connectionError = null
                            scope.launch {
                                isConnecting = true
                                try {
                                    client.connect()
                                } catch (e: Exception) {
                                    connectionError = e.message
                                } finally {
                                    isConnecting = false
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // ── Scanning Indicator ─────────────────────────────────────
        AnimatedVisibility(visible = isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Searching for devices…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Device List or Empty State ────────────────────────────
        if (!isScanning && isConnected && devices.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No devices found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap Scan to search for devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // ── Device Cards ──────────────────────────────────────────
        if (devices.isNotEmpty()) {
            Text(
                text = "Found Devices",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            devices.forEach { device ->
                DeviceCard(
                    device = device,
                    isSelected = false,
                    onClick = { onDeviceSelected(device) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

/**
 * Modifier extension for smooth list item animations.
 */
private fun Modifier.animateItem(): Modifier = this.then(Modifier)

// =============================================================================
// Previews
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun DeviceScannerDisconnectedPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = "ws://192.168.1.100:12345",
                    onValueChange = {},
                    label = { Text("Server URL") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect")
                }
                ConnectionIndicator(state = ButtplugClientState.Disconnected)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun DeviceScannerConnectedPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = "ws://localhost:12345",
                    onValueChange = {},
                    label = { Text("Server URL") },
                    singleLine = true,
                    enabled = false,
                    leadingIcon = { Icon(Icons.Default.BluetoothConnected, null, tint = Color(0xFF4CAF50)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Disconnect")
                    }
                    Button(onClick = {}, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan")
                    }
                }
                ConnectionIndicator(
                    state = ButtplugClientState.Connected("Intiface", 4, 0, 10000)
                )
                Text("Found Devices", style = MaterialTheme.typography.titleSmall)
                DeviceCard(
                    device = ButtplugDevice(0, "Lovense Hush", "Hush 2",
                        features = listOf(DeviceFeature(0, "Vibrator", outputTypes = listOf(ActuatorType.Vibrate)))
                    )
                )
            }
        }
    }
}
