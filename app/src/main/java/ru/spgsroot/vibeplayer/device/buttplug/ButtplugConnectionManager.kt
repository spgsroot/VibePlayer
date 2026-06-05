package ru.spgsroot.vibeplayer.device.buttplug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
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

    private var webSocket: WebSocket? = null
    private var serverInfoReceived = false
    private var pendingScan = false
    private val messageIdCounter = AtomicInteger(1)
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun connect(url: String = DEFAULT_URL) {
        val normalizedUrl = url.trim()
        validateWebSocketUrl(normalizedUrl)?.let { error ->
            _state.value = DeviceState.Error(error)
            return
        }

        val request = runCatching {
            Request.Builder()
                .url(normalizedUrl)
                .build()
        }.getOrElse { error ->
            _state.value = DeviceState.Error(error.message ?: "Invalid server URL")
            return
        }

        webSocket?.close(1000, "Reconnect")
        _devices.value = emptyList()
        serverInfoReceived = false
        pendingScan = true
        Log.d(TAG, "Connecting to $normalizedUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@ButtplugConnectionManager.webSocket = webSocket
                Log.d(TAG, "WebSocket opened")
                _state.value = DeviceState.Scanning
                requestServerInfo()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}, response=${response?.code}", t)
                _state.value = DeviceState.Error(t.message ?: "Connection failed")
                if (this@ButtplugConnectionManager.webSocket === webSocket) {
                    this@ButtplugConnectionManager.webSocket = null
                    serverInfoReceived = false
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                if (this@ButtplugConnectionManager.webSocket === webSocket) {
                    _state.value = DeviceState.Disconnected
                    this@ButtplugConnectionManager.webSocket = null
                    serverInfoReceived = false
                }
            }
        })
    }

    fun disconnect() {
        if (webSocket != null) {
            sendMessage("StopAllDevices", """{"Id":${nextMessageId()}}""")
        }
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _devices.value = emptyList()
        pendingScan = false
        serverInfoReceived = false
        _state.value = DeviceState.Disconnected
    }

    fun sendCommand(command: String) {
        val message = command.asButtplugMessageArray()
        Log.d(TAG, "TX command: $message")
        webSocket?.send(message)
    }

    fun startScanning() {
        pendingScan = true
        if (serverInfoReceived) {
            sendStartScanning()
        }
    }

    private fun handleMessage(text: String) {
        Log.d(TAG, "RX: $text")
        runCatching {
            val root = json.parseToJsonElement(text)
            val messages = when (root) {
                is JsonArray -> root
                is JsonObject -> listOf(root)
                else -> emptyList()
            }

            messages.forEach { message ->
                val messageObject = message as? JsonObject ?: return@forEach
                val envelope = messageObject.entries.firstOrNull() ?: return@forEach
                val payload = envelope.value as? JsonObject ?: return@forEach

                when (envelope.key) {
                    "ServerInfo" -> handleServerInfo(payload)
                    "DeviceList" -> handleDeviceList(payload)
                    "DeviceAdded" -> handleDeviceAdded(payload)
                    "DeviceRemoved" -> handleDeviceRemoved(payload)
                    "ScanningFinished" -> handleScanningFinished()
                    "Ok" -> Log.d(TAG, "Server OK for Id=${payload["Id"]?.jsonPrimitive?.contentOrNull}")
                    "Error" -> {
                        val errorMessage = payload["ErrorMessage"]?.jsonPrimitive?.contentOrNull
                            ?: "Buttplug server error"
                        Log.e(TAG, "Server error: $errorMessage")
                        _state.value = DeviceState.Error(errorMessage)
                    }
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to parse Buttplug message: $text", e)
        }
    }

    private fun handleServerInfo(payload: JsonObject) {
        serverInfoReceived = true
        Log.d(TAG, "ServerInfo received: $payload")
        requestDeviceList()

        val maxPingTime = payload["MaxPingTime"]?.jsonPrimitive?.longOrNull ?: 0L
        if (maxPingTime > 0L) {
            sendMessage("Ping", """{"Id":${nextMessageId()}}""")
        }
    }

    private fun handleDeviceList(payload: JsonObject) {
        val parsedDevices = when (val devicesPayload = payload["Devices"]) {
            is JsonArray -> devicesPayload.mapNotNull { parseDevice(it) }
            is JsonObject -> devicesPayload.values.mapNotNull { parseDevice(it) }
            else -> emptyList()
        }

        if (parsedDevices.isNotEmpty()) {
            Log.d(TAG, "DeviceList parsed: ${parsedDevices.size} devices")
            _devices.value = parsedDevices
            _state.value = DeviceState.Connected(parsedDevices.first().name)
        } else {
            Log.d(TAG, "DeviceList is empty")
        }

        if (pendingScan) {
            sendStartScanning()
        }
    }

    private fun handleDeviceAdded(payload: JsonObject) {
        val device = parseDevice(payload) ?: return
        Log.d(TAG, "DeviceAdded: index=${device.index}, name=${device.name}, capabilities=${device.capabilities}")
        _devices.value = (_devices.value.filter { it.index != device.index } + device)
            .sortedBy { it.index }
        _state.value = DeviceState.Connected(device.name)
    }

    private fun handleDeviceRemoved(payload: JsonObject) {
        val deviceIndex = payload["DeviceIndex"]?.jsonPrimitive?.intOrNull ?: return
        Log.d(TAG, "DeviceRemoved: index=$deviceIndex")
        _devices.value = _devices.value.filter { it.index != deviceIndex }
        if (_devices.value.isEmpty()) {
            _state.value = DeviceState.Disconnected
        }
    }

    private fun handleScanningFinished() {
        val firstDevice = _devices.value.firstOrNull()
        if (firstDevice != null) {
            _state.value = DeviceState.Connected(firstDevice.name)
        } else {
            Log.d(TAG, "Scanning finished with no devices")
            _state.value = DeviceState.Error("No devices found by Intiface")
        }
    }

    private fun parseDevice(element: JsonElement): ButtplugDevice? {
        val device = element as? JsonObject ?: return null
        val index = device["DeviceIndex"]?.jsonPrimitive?.intOrNull ?: return null
        val name = device["DeviceDisplayName"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: device["DeviceName"]?.jsonPrimitive?.contentOrNull
            ?: "Device $index"
        val capabilities = (device["DeviceMessages"] as? JsonObject)?.keys?.toList()
            ?: (device["DeviceFeatures"] as? JsonObject)?.values
                ?.flatMap { feature ->
                    ((feature as? JsonObject)?.get("Output") as? JsonObject)?.keys.orEmpty()
                }
            ?: emptyList()

        return ButtplugDevice(
            index = index,
            name = name,
            capabilities = capabilities
        )
    }

    private fun requestServerInfo() {
        sendMessage(
            name = "RequestServerInfo",
            body = """{"Id":${nextMessageId()},"ClientName":"$CLIENT_NAME","MessageVersion":$MESSAGE_VERSION}"""
        )
    }

    private fun requestDeviceList() {
        sendMessage("RequestDeviceList", """{"Id":${nextMessageId()}}""")
    }

    private fun sendStartScanning() {
        pendingScan = false
        if (_devices.value.isEmpty()) {
            _state.value = DeviceState.Scanning
        }
        sendMessage("StartScanning", """{"Id":${nextMessageId()}}""")
    }

    private fun sendMessage(name: String, body: String) {
        val message = """[{"$name":$body}]"""
        Log.d(TAG, "TX: $message")
        webSocket?.send(message)
    }

    private fun nextMessageId(): Int = messageIdCounter.getAndIncrement()

    private fun String.asButtplugMessageArray(): String {
        val trimmed = trim()
        return if (trimmed.startsWith("[")) trimmed else "[$trimmed]"
    }

    private fun validateWebSocketUrl(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return "Invalid server URL"
        val scheme = uri.scheme?.lowercase()
        val host = uri.host

        if (scheme != "ws" && scheme != "wss") {
            return "Server URL must use ws:// or wss://"
        }
        if (host.isNullOrBlank()) {
            return "Server URL must include a host"
        }

        return null
    }
}
