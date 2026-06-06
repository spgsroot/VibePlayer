package ru.spgsroot.vibeplayer.device.buttplug

import android.util.Log
import io.github.spgsroot.buttplug.ButtplugClient
import io.github.spgsroot.buttplug.ButtplugClientConfig
import io.github.spgsroot.buttplug.ButtplugClientState
import io.github.spgsroot.buttplug.device.ActuatorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        private const val PROTOCOL_VERSION_MAJOR = 4
        private const val PROTOCOL_VERSION_MINOR = 0
    }

    private val _state = MutableStateFlow<DeviceState>(DeviceState.Disconnected)
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<ButtplugDevice>>(emptyList())
    val devices: StateFlow<List<ButtplugDevice>> = _devices.asStateFlow()

    /** Raw library device list with full feature information. */
    private val _rawDevices = MutableStateFlow<List<io.github.spgsroot.buttplug.device.ButtplugDevice>>(emptyList())
    val rawDevices: List<io.github.spgsroot.buttplug.device.ButtplugDevice> get() = _rawDevices.value

    private var client: ButtplugClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track collection jobs to cancel on reconnect
    private var stateCollectionJob: Job? = null
    private var deviceCollectionJob: Job? = null
    private var connectionJob: Job? = null

    // Map library ButtplugClientState to our DeviceState
    private fun mapState(bs: ButtplugClientState): DeviceState = when (bs) {
        is ButtplugClientState.Disconnected -> DeviceState.Disconnected
        is ButtplugClientState.Connecting -> DeviceState.Scanning
        is ButtplugClientState.Handshaking -> DeviceState.Scanning
        is ButtplugClientState.Connected -> DeviceState.Connected(bs.serverName)
        is ButtplugClientState.Scanning -> DeviceState.Scanning
        is ButtplugClientState.Reconnecting -> DeviceState.Scanning
        is ButtplugClientState.Error -> DeviceState.Error(bs.reason)
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
        Log.d(TAG, "connect() called with URL: $url")
        stateCollectionJob?.cancel()
        deviceCollectionJob?.cancel()
        connectionJob?.cancel()

        client?.disconnect()

        val cfg = ButtplugClientConfig(
            serverUrl = url.trim(),
            clientName = CLIENT_NAME,
            protocolVersionMajor = PROTOCOL_VERSION_MAJOR,
            protocolVersionMinor = PROTOCOL_VERSION_MINOR,
            maxReconnectAttempts = 0
        )
        val newClient = ButtplugClient(cfg)
        client = newClient

        stateCollectionJob = scope.launch {
            newClient.state.collect { bs -> _state.value = mapState(bs) }
        }
        deviceCollectionJob = scope.launch {
            newClient.devices.collect { libDevices ->
                _rawDevices.value = libDevices
                _devices.value = libDevices.map { mapDevice(it) }
            }
        }
        connectionJob = scope.launch {
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
        stateCollectionJob?.cancel()
        deviceCollectionJob?.cancel()
        connectionJob?.cancel()
        stateCollectionJob = null
        deviceCollectionJob = null
        connectionJob = null

        client?.disconnect()
        client = null
        _state.value = DeviceState.Disconnected
        _devices.value = emptyList()
    }

    fun startScanning() {
        scope.launch {
            try { client?.startScanning() } catch (e: Exception) {
                Log.e(TAG, "Scan failed: ${e.message}", e)
            }
        }
    }

    /**
     * Send a scalar actuator command through the underlying ButtplugClient.
     * Generic method supporting all actuator types (Vibrate, Rotate, Oscillate, etc.).
     */
    internal suspend fun sendActuator(
        deviceIndex: Int, featureIndex: Int, actuator: ActuatorType, speed: Double
    ) {
        try {
            client?.sendActuatorCommand(deviceIndex, featureIndex, actuator, speed)
        } catch (e: Exception) {
            Log.w(TAG, "sendActuator($actuator) failed: ${e.message}")
        }
    }

    /**
     * Send a stop command through the underlying ButtplugClient.
     */
    internal suspend fun sendStop(deviceIndex: Int?) {
        try {
            client?.sendStop(deviceIndex)
        } catch (e: Exception) {
            Log.w(TAG, "sendStop failed: ${e.message}")
        }
    }
}
