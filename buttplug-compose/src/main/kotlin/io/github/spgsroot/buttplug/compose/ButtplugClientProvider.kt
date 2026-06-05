package io.github.spgsroot.buttplug.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.spgsroot.buttplug.ButtplugClient
import io.github.spgsroot.buttplug.ButtplugClientConfig
import io.github.spgsroot.buttplug.ButtplugClientState
import io.github.spgsroot.buttplug.device.ButtplugDevice

/**
 * Compose-friendly state holder wrapping [ButtplugClient].
 *
 * Provides [state] and [devices] as Compose [State] objects that
 * automatically track the underlying [kotlinx.coroutines.flow.StateFlow]s.
 * The client is automatically disconnected when the [ButtplugClientHolder]
 * leaves composition.
 */
class ButtplugClientHolder(
    val client: ButtplugClient,
    val state: State<ButtplugClientState>,
    val devices: State<List<ButtplugDevice>>
)

/**
 * Remembers a [ButtplugClient] and its derived state across recompositions.
 *
 * The client is created once with [remember] and automatically disconnected
 * via [DisposableEffect] when leaving composition.
 *
 * Usage:
 * ```
 * val holder = rememberButtplugClient(
 *     config = ButtplugClientConfig(serverUrl = "ws://192.168.1.100:12345")
 * )
 * val connectionState = holder.state.value
 * val deviceList = holder.devices.value
 * ```
 *
 * @param config Configuration for the client connection.
 * @param client Optional pre-created [ButtplugClient]. If not provided,
 *               a new instance is created with [remember].
 * @return A [ButtplugClientHolder] containing the client and reactive state.
 */
@Composable
fun rememberButtplugClient(
    config: ButtplugClientConfig = ButtplugClientConfig.DEFAULT,
    client: ButtplugClient = remember { ButtplugClient(config) }
): ButtplugClientHolder {
    val connectionState = remember { mutableStateOf<ButtplugClientState>(ButtplugClientState.Disconnected) }
    val deviceList = remember { mutableStateOf<List<ButtplugDevice>>(emptyList()) }

    // Collect StateFlows into Compose State
    LaunchedEffect(client) {
        client.state.collect { connectionState.value = it }
    }

    LaunchedEffect(client) {
        client.devices.collect { deviceList.value = it }
    }

    // Auto-disconnect on dispose
    DisposableEffect(client) {
        onDispose {
            client.disconnect()
        }
    }

    return remember(client, connectionState, deviceList) {
        ButtplugClientHolder(
            client = client,
            state = connectionState,
            devices = deviceList
        )
    }
}
