package ru.spgsroot.vibeplayer.device.buttplug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ButtplugConnectionManager @Inject constructor() {

    private val _state = MutableStateFlow<DeviceState>(DeviceState.Disconnected)
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<ButtplugDevice>>(emptyList())
    val devices: StateFlow<List<ButtplugDevice>> = _devices.asStateFlow()

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun connect() {
        val request = Request.Builder()
            .url("ws://127.0.0.1:12345")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = DeviceState.Scanning
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = DeviceState.Error(t.message ?: "Connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = DeviceState.Disconnected
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }

    fun sendCommand(command: String) {
        webSocket?.send(command)
    }

    fun startScanning() {
        webSocket?.send("""{"StartScanning":{"Id":1}}""")
    }

    private fun handleMessage(text: String) {
        try {
            when {
                text.contains("DeviceAdded") -> {
                    val msg = json.decodeFromString<ButtplugMessage.DeviceAdded>(text)
                    val device = ButtplugDevice(
                        index = msg.deviceIndex,
                        name = msg.deviceName,
                        capabilities = msg.deviceMessages.keys.toList()
                    )
                    _devices.value = _devices.value + device
                }
                text.contains("DeviceRemoved") -> {
                    val msg = json.decodeFromString<ButtplugMessage.DeviceRemoved>(text)
                    _devices.value = _devices.value.filter { it.index != msg.deviceIndex }
                }
                text.contains("Ok") -> {
                    // Command acknowledged
                }
                text.contains("Error") -> {
                    val msg = json.decodeFromString<ButtplugMessage.Error>(text)
                    _state.value = DeviceState.Error(msg.errorMessage)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
