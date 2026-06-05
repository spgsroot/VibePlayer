package ru.spgsroot.vibeplayer.device.buttplug

import android.util.Log
import io.github.spgsroot.buttplug.ButtplugClient
import io.github.spgsroot.buttplug.ButtplugClientConfig
import io.github.spgsroot.buttplug.ButtplugClientState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtplugConnectionManager @Inject constructor() {

    companion object {
        const val DEFAULT_URL = "ws://192.168.10.221:12345"
        private const val TAG = "ButtplugConnection"
        private const val CLIENT_NAME = "VibePlayer"
        private const val MESSAGE_VERSION = 3
    }

    private val _state = MutableStateFlow<DeviceState>(DeviceState.Disconnected)
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<ButtplugDevice>>(emptyList())
    val devices: StateFlow<List<ButtplugDevice>> = _devices.asStateFlow()

    private var client: ButtplugClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Exposed for use by compose components like DeviceScanner. */
    internal val buttplugClient: ButtplugClient?
        get() = client

    // Map ButtplugClientState to our DeviceState
    private fun updateState(bs: ButtplugClientState) {
        _state.value = when (bs) {
            is ButtplugClientState.Disconnected -> DeviceState.Disconnected
            is ButtplugClientState.Connecting -> DeviceState.Scanning
            is ButtplugClientState.Handshaking -> DeviceState.Scanning
            is ButtplugClientState.Connected -> DeviceState.Connected(bs.serverName)
            is ButtplugClientState.Scanning -> DeviceState.Scanning
            is ButtplugClientState.Reconnecting -> DeviceState.Scanning
            is ButtplugClientState.Error -> DeviceState.Error(bs.reason)
        }
    }

    // Map library ButtplugDevice to our ButtplugDevice
    private fun mapDevice(d: io.github.spgsroot.buttplug.device.ButtplugDevice): ButtplugDevice {
        val capabilities = d.features.flatMap { f ->
            f.outputTypes.map { it.name } + f.inputTypes.map { it.name }
        }
        return ButtplugDevice(
            index = d.index,
            name = d.displayName.ifBlank { d.name },
            capabilities = capabilities
        )
    }

    fun connect(url: String = DEFAULT_URL) {
        client?.disconnect()
        val cfg = ButtplugClientConfig(
            serverUrl = url.trim(),
            clientName = CLIENT_NAME,
            protocolVersionMajor = MESSAGE_VERSION
        )
        val newClient = ButtplugClient(cfg)
        client = newClient

        // Collect state changes from the library client
        scope.launch {
            newClient.state.collect { bs -> updateState(bs) }
        }
        // Collect device list changes
        scope.launch {
            newClient.devices.collect { libDevices ->
                _devices.value = libDevices.map { mapDevice(it) }
            }
        }
        // Initiate connection
        scope.launch {
            try {
                newClient.connect()
                newClient.requestDeviceList()
                newClient.startScanning()
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                _state.value = DeviceState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _state.value = DeviceState.Disconnected
        _devices.value = emptyList()
    }

    fun startScanning() {
        scope.launch {
            try {
                client?.startScanning()
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed: ${e.message}", e)
            }
        }
    }

    /** Kept for backward compatibility; no-op with new API. */
    fun sendCommand(command: String) {
        // No-op: command sending is now done via sendVibrate/sendStop
    }

    /** Send a vibrate command through the underlying ButtplugClient. */
    internal suspend fun sendVibrate(deviceIndex: Int, featureIndex: Int, speed: Double) {
        client?.sendVibrate(deviceIndex, featureIndex, speed)
    }

    /** Send a stop command through the underlying ButtplugClient. */
    internal suspend fun sendStop(deviceIndex: Int?) {
        client?.sendStop(deviceIndex)
    }
}
