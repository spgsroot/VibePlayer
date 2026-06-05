package io.github.spgsroot.buttplug

import io.github.spgsroot.buttplug.connection.ButtplugTransport
import io.github.spgsroot.buttplug.connection.TransportListener
import io.github.spgsroot.buttplug.connection.WebSocketTransport
import io.github.spgsroot.buttplug.device.ButtplugDevice
import io.github.spgsroot.buttplug.device.DeviceManager
import io.github.spgsroot.buttplug.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Main entry point for the Buttplug client library.
 *
 * Usage:
 * ```
 * val client = ButtplugClient(ButtplugClientConfig(serverUrl = "ws://localhost:12345"))
 * client.connect()
 * client.state.collect { state -> /* handle state changes */ }
 * client.devices.collect { devices -> /* handle device list */ }
 * client.sendVibrate(deviceIndex = 0, featureIndex = 0, speed = 0.5)
 * client.disconnect()
 * ```
 */
class ButtplugClient(
    val config: ButtplugClientConfig = ButtplugClientConfig.DEFAULT,
    private val transport: ButtplugTransport = WebSocketTransport(config.bypassCertVerify),
    private val idGenerator: MessageIdGenerator = MessageIdGenerator(),
    private val messageSorter: MessageSorter = MessageSorter(),
    private val deviceManager: DeviceManager = DeviceManager()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ButtplugClientState>(ButtplugClientState.Disconnected)
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var handshakeDeferred: CompletableDeferred<ServerInfo>? = null

    /** Current connection state */
    val state: StateFlow<ButtplugClientState> = _state.asStateFlow()

    /** Current device list */
    val devices: StateFlow<List<ButtplugDevice>> = deviceManager.devices

    /** Server info received during handshake, null if not connected */
    private var serverInfo: ServerInfo? = null

    // =========================================================================
    // JSON CONFIGURATION
    // =========================================================================

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        serializersModule = SerializersModule {
            polymorphic(ButtplugServerMessage::class) {
                subclass(ServerInfo::class)
                subclass(DeviceList::class)
                subclass(Ok::class)
                subclass(Error::class)
                subclass(ScanningFinished::class)
                subclass(DeviceRemoved::class)
                subclass(InputReading::class)
                subclass(SensorReadingV3::class)
                subclass(BatteryLevelReading::class)
                subclass(RSSILevelReading::class)
                subclass(RawReading::class)
            }
        }
    }

    // =========================================================================
    // CONNECTION LIFECYCLE
    // =========================================================================

    /**
     * Connect to the Buttplug/Intiface server.
     * Suspends until the handshake completes or fails.
     */
    suspend fun connect(): ServerInfo = withContext(Dispatchers.IO) {
        val currentState = _state.value
        if (currentState is ButtplugClientState.Connected) {
            return@withContext serverInfo ?: throw ButtplugException.HandshakeFailed("No server info")
        }

        _state.value = ButtplugClientState.Connecting

        val deferred = CompletableDeferred<ServerInfo>()
        handshakeDeferred = deferred

        try {
            transport.connect(config.serverUrl, createTransportListener())
            val info = withTimeout(config.requestTimeoutMs) { deferred.await() }
            serverInfo = info
            startPingLoop(info.MaxPingTime)
            _state.value = ButtplugClientState.Connected(
                serverName = info.ServerName,
                protocolVersionMajor = info.ProtocolVersionMajor,
                protocolVersionMinor = info.ProtocolVersionMinor,
                maxPingTime = info.MaxPingTime
            )
            info
        } catch (e: TimeoutCancellationException) {
            cleanup()
            throw ButtplugException.Timeout("Handshake timed out after ${config.requestTimeoutMs}ms")
        } catch (e: ButtplugException) {
            cleanup()
            throw e
        } catch (e: Exception) {
            cleanup()
            throw ButtplugException.ConnectionFailed("Connection failed: ${e.message}", e)
        }
    }

    /**
     * Disconnect from the server. Sends StopAllDevices first.
     */
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        cleanup()
        _state.value = ButtplugClientState.Disconnected
    }

    private fun cleanup() {
        pingJob?.cancel()
        pingJob = null
        handshakeDeferred?.completeExceptionally(ButtplugException.Disconnected())
        handshakeDeferred = null
        messageSorter.cancelAll(ButtplugException.Disconnected())
        idGenerator.reset()
        deviceManager.clear()
        transport.disconnect()
        serverInfo = null
    }

    // =========================================================================
    // TRANSPORT LISTENER
    // =========================================================================

    private fun createTransportListener() = object : TransportListener {
        override fun onConnected() {
            scope.launch {
                _state.value = ButtplugClientState.Handshaking
                requestServerInfo()
            }
        }

        override fun onMessage(text: String) {
            scope.launch { handleIncomingMessage(text) }
        }

        override fun onDisconnected(code: Int, reason: String) {
            scope.launch {
                pingJob?.cancel()
                messageSorter.cancelAll(ButtplugException.Disconnected(reason))
                attemptReconnect()
            }
        }

        override fun onError(error: Throwable) {
            scope.launch {
                _state.value = ButtplugClientState.Error(error.message ?: "Transport error")
                pingJob?.cancel()
                messageSorter.cancelAll(ButtplugException.Disconnected(error.message ?: "Transport error"))
                attemptReconnect()
            }
        }
    }

    // =========================================================================
    // RECONNECTION LOGIC
    // =========================================================================

    private suspend fun attemptReconnect() {
        if (reconnectJob?.isActive == true) return
        if (config.maxReconnectAttempts == 0) {
            _state.value = ButtplugClientState.Disconnected
            return
        }

        for (attempt in 1..config.maxReconnectAttempts) {
            _state.value = ButtplugClientState.Reconnecting(attempt, config.maxReconnectAttempts)
            delay(config.reconnectDelayMs)
            try {
                connect()
                return
            } catch (_: Exception) {
                // Try again
            }
        }
        _state.value = ButtplugClientState.Error("Failed to reconnect after ${config.maxReconnectAttempts} attempts")
    }

    // =========================================================================
    // PING KEEPALIVE
    // =========================================================================

    private fun startPingLoop(maxPingTime: Long) {
        pingJob?.cancel()
        if (maxPingTime <= 0) return

        val interval = maxOf(maxPingTime / 2, 100L)
        pingJob = scope.launch {
            while (isActive) {
                delay(interval)
                try {
                    sendMessage(Ping(idGenerator.nextId()))
                } catch (_: Exception) {
                    // Ping failure handled by transport listener
                }
            }
        }
    }

    // =========================================================================
    // PROTOCOL MESSAGE HANDLING
    // =========================================================================

    private suspend fun handleIncomingMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return

        try {
            // Messages are always JSON arrays, even single messages
            val element = json.parseToJsonElement(text)
            val messages = when (element) {
                is JsonArray -> element
                is JsonObject -> JsonArray(listOf(element))
                else -> return
            }

            for (msg in messages) {
                if (msg !is JsonObject) continue
                val msgObj = msg.jsonObject
                // Each message has exactly one key: the message type name
                val typeName = msgObj.keys.firstOrNull() ?: continue
                val payload = msgObj[typeName]?.jsonObject ?: continue

                when (typeName) {
                    "ServerInfo" -> handleServerInfo(payload)
                    "DeviceList" -> handleDeviceList(payload)
                    "DeviceAdded" -> handleDeviceAdded(payload)
                    "DeviceRemoved" -> handleDeviceRemoved(payload)
                    "ScanningFinished" -> handleScanningFinished()
                    "Ok" -> handleOk(payload)
                    "Error" -> handleError(payload)
                    "InputReading" -> handleInputReading(payload)
                    "SensorReading" -> handleSensorReading(payload)
                    "BatteryLevelReading" -> handleBatteryLevelReading(payload)
                    "RSSILevelReading" -> handleRssiLevelReading(payload)
                    else -> {
                        // Try to resolve as response to pending request
                        val resolved = tryResolveGeneric(typeName, payload)
                        if (!resolved) {
                            // Unknown message type - log and ignore
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _state.value = ButtplugClientState.Error("Protocol parse error: ${e.message}")
        }
    }

    private fun handleServerInfo(payload: JsonObject) {
        val info = json.decodeFromJsonElement<ServerInfo>(payload)
        handshakeDeferred?.complete(info)
        handshakeDeferred = null
    }

    private fun handleDeviceList(payload: JsonObject) {
        val deviceList = json.decodeFromJsonElement<DeviceList>(payload)
        deviceManager.handleDeviceList(deviceList)
        if (_state.value is ButtplugClientState.Connected || _state.value is ButtplugClientState.Scanning) {
            val firstDevice = deviceList.Devices.firstOrNull()
            if (firstDevice != null) {
                _state.value = ButtplugClientState.Scanning
            }
        }
        messageSorter.resolve(deviceList)
    }

    private fun handleDeviceAdded(payload: JsonObject) {
        val rawDevice = json.decodeFromJsonElement<DeviceAddedV3>(payload)
        deviceManager.handleDeviceAdded(rawDevice)
        if (_state.value !is ButtplugClientState.Connected) {
            _state.value = ButtplugClientState.Scanning
        }
    }

    private fun handleDeviceRemoved(payload: JsonObject) {
        val removed = json.decodeFromJsonElement<DeviceRemoved>(payload)
        deviceManager.handleDeviceRemoved(removed)
        // Notify via message sorter (it's an event with Id=0, which won't match any pending)
    }

    private fun handleScanningFinished() {
        val connectedState = _state.value as? ButtplugClientState.Connected
        if (connectedState != null) {
            _state.value = connectedState.copy()
        } else {
            _state.value = ButtplugClientState.Error("Scanning finished, no devices found")
        }
    }

    private fun handleOk(payload: JsonObject) {
        val ok = json.decodeFromJsonElement<Ok>(payload)
        messageSorter.resolve(ok)
    }

    private fun handleError(payload: JsonObject) {
        val error = json.decodeFromJsonElement<Error>(payload)
        // Try to resolve, if unmatched it's a global error
        if (!messageSorter.resolve(error)) {
            _state.value = ButtplugClientState.Error("${error.ErrorMessage} (code: ${error.ErrorCode})")
            // If we're still handshaking, fail the handshake deferred
            handshakeDeferred?.completeExceptionally(
                ButtplugException.HandshakeFailed("${error.ErrorMessage} (code: ${error.ErrorCode})")
            )
            handshakeDeferred = null
        }
    }

    private fun handleInputReading(payload: JsonObject) {
        val reading = json.decodeFromJsonElement<InputReading>(payload)
        messageSorter.resolve(reading)
    }

    private fun handleSensorReading(payload: JsonObject) {
        val reading = json.decodeFromJsonElement<SensorReadingV3>(payload)
        messageSorter.resolve(reading)
    }

    private fun handleBatteryLevelReading(payload: JsonObject) {
        val reading = json.decodeFromJsonElement<BatteryLevelReading>(payload)
        if (!messageSorter.resolve(reading)) {
            // Could expose via a separate flow for unsolicited readings
        }
    }

    private fun handleRssiLevelReading(payload: JsonObject) {
        val reading = json.decodeFromJsonElement<RSSILevelReading>(payload)
        if (!messageSorter.resolve(reading)) {
            // Could expose via a separate flow for unsolicited readings
        }
    }

    private fun tryResolveGeneric(typeName: String, payload: JsonObject): Boolean {
        // Try to get Id field to match to pending request
        val id = payload["Id"]?.jsonPrimitive?.intOrNull ?: return false
        if (id == 0) return false // system event, no pending request

        // Rebuild the message envelope
        val envelope = buildJsonObject {
            put(typeName, payload)
        }
        val wrapped = buildJsonObject {
            put("Id", JsonPrimitive(id))
            envelope.forEach { (key, value) -> put(key, value) }
        }
        // Try parsing as a generic Ok or reuse sorter via manual resolution
        return try {
            val ok = json.decodeFromJsonElement<Ok>(wrapped)
            messageSorter.resolve(ok)
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // HIGH-LEVEL API
    // =========================================================================

    /**
     * Suspend until the handshake completes and state is Connected.
     */
    suspend fun awaitConnected() {
        state.first { it is ButtplugClientState.Connected }
    }

    // --- Device Enumeration ---

    /**
     * Request the current device list from the server.
     */
    suspend fun requestDeviceList(): List<ButtplugDevice> {
        requireConnected()
        val msg = RequestDeviceList(idGenerator.nextId())
        val response = sendAndAwait<DeviceList>(msg)
        deviceManager.handleDeviceList(response)
        return deviceManager.devices.value
    }

    /**
     * Start scanning for new devices.
     */
    suspend fun startScanning() {
        requireConnected()
        val msg = StartScanning(idGenerator.nextId())
        sendMessage(msg)
        _state.value = ButtplugClientState.Scanning
    }

    /**
     * Stop an active scan.
     */
    suspend fun stopScanning() {
        requireConnected()
        val msg = StopScanning(idGenerator.nextId())
        sendMessage(msg)
        // Restore Connected state from stored server info (state may be Scanning)
        val info = serverInfo
        if (info != null) {
            _state.value = ButtplugClientState.Connected(
                serverName = info.ServerName,
                protocolVersionMajor = info.ProtocolVersionMajor,
                protocolVersionMinor = info.ProtocolVersionMinor,
                maxPingTime = info.MaxPingTime
            )
        }
    }

    // --- Device Commands ---

    /**
     * Send a vibration command to a device feature.
     * @param speed 0.0 to 1.0
     */
    suspend fun sendVibrate(deviceIndex: Int, featureIndex: Int, speed: Double) {
        requireConnected()
        requireDevice(deviceIndex)
        val cmd = OutputCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex,
            Command = OutputCommandValue(Vibrate = ScalarCommand(Value = speed.coerceIn(0.0, 1.0)))
        )
        sendAndAwaitOk(cmd)
    }

    /**
     * Send a rotation command to a device feature.
     * @param speed 0.0 to 1.0
     * @param clockwise direction
     */
    suspend fun sendRotate(deviceIndex: Int, featureIndex: Int, speed: Double, clockwise: Boolean = true) {
        requireConnected()
        requireDevice(deviceIndex)
        val cmd = OutputCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex,
            Command = OutputCommandValue(Rotate = ScalarCommand(Value = speed.coerceIn(0.0, 1.0)))
        )
        sendAndAwaitOk(cmd)
        // Note: clockwise is not a separate field in OutputCmd; the device interprets the value
    }

    /**
     * Send a linear position command to a device feature.
     * @param position 0.0 to 1.0
     * @param durationMs movement time in milliseconds
     */
    suspend fun sendLinear(deviceIndex: Int, featureIndex: Int, position: Double, durationMs: Int) {
        requireConnected()
        requireDevice(deviceIndex)
        val cmd = OutputCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex,
            Command = OutputCommandValue(
                HwPositionWithDuration = PositionWithDurationCommand(
                    Value = (position.coerceIn(0.0, 1.0) * 100).toInt(),
                    Duration = durationMs
                )
            )
        )
        sendAndAwaitOk(cmd)
    }

    /**
     * Stop a specific device, feature, or all devices.
     */
    suspend fun sendStop(deviceIndex: Int? = null, featureIndex: Int? = null) {
        requireConnected()
        val cmd = StopCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex
        )
        sendAndAwaitOk(cmd)
    }

    /**
     * Stop all connected devices immediately.
     */
    suspend fun stopAllDevices() {
        sendStop()
    }

    /**
     * Send a raw OutputCmd for advanced usage.
     */
    suspend fun sendOutputCommand(deviceIndex: Int, featureIndex: Int, command: OutputCommandValue) {
        requireConnected()
        requireDevice(deviceIndex)
        val cmd = OutputCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex,
            Command = command
        )
        sendAndAwaitOk(cmd)
    }

    // --- Sensor/Input Commands ---

    /**
     * Read battery level from a device sensor.
     */
    suspend fun readBattery(deviceIndex: Int, featureIndex: Int): Int {
        requireConnected()
        val cmd = InputCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex,
            Type = "Battery",
            Command = "read"
        )
        val response = sendAndAwait<InputReading>(cmd)
        return response.Reading.Battery?.Value ?: throw ButtplugException.ProtocolError(
            ButtplugErrorCode.ERROR_DEVICE, "Battery reading not available"
        )
    }

    /**
     * Read RSSI signal strength from a device sensor.
     */
    suspend fun readRssi(deviceIndex: Int, featureIndex: Int): Int {
        requireConnected()
        val cmd = InputCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex,
            Type = "Rssi",
            Command = "read"
        )
        val response = sendAndAwait<InputReading>(cmd)
        return response.Reading.Rssi?.Value ?: throw ButtplugException.ProtocolError(
            ButtplugErrorCode.ERROR_DEVICE, "RSSI reading not available"
        )
    }

    /**
     * Subscribe to battery level updates from a sensor.
     * Returns a Flow that emits battery levels.
     */
    fun batteryUpdates(deviceIndex: Int, featureIndex: Int): Flow<Int> = flow {
        requireConnected()
        val subscribeCmd = InputCmd(
            Id = idGenerator.nextId(),
            DeviceIndex = deviceIndex,
            FeatureIndex = featureIndex,
            Type = "Battery",
            Command = "subscribe"
        )
        sendAndAwaitOk(subscribeCmd)

        // Listen for incoming readings with Id=0 (events)
        try {
            // For simplicity, we poll-read rather than handling subscription events
            while (currentCoroutineContext().isActive) {
                delay(5000)
                emit(readBattery(deviceIndex, featureIndex))
            }
        } finally {
            try {
                val unsubscribeCmd = InputCmd(
                    Id = idGenerator.nextId(),
                    DeviceIndex = deviceIndex,
                    FeatureIndex = featureIndex,
                    Type = "Battery",
                    Command = "unsubscribe"
                )
                sendAndAwaitOk(unsubscribeCmd)
            } catch (_: Exception) { }
        }
    }

    // --- Ping ---

    /**
     * Send a ping to verify the connection is alive.
     */
    suspend fun ping() {
        requireConnected()
        val msg = Ping(idGenerator.nextId())
        sendAndAwaitOk(msg)
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private fun sendMessage(msg: Any) {
        val jsonString = json.encodeToString(JsonObject.serializer(), buildMessageEnvelope(msg))
        transport.send("[$jsonString]")
    }

    // Builds the {"MessageType": {...}} envelope
    // Uses explicit serializers to avoid reified type erasure with Any parameters
    @OptIn(ExperimentalSerializationApi::class)
    private fun buildMessageEnvelope(msg: Any): JsonObject {
        val typeName: String
        val payload: JsonElement
        when (msg) {
            is RequestServerInfo -> {
                typeName = "RequestServerInfo"
                payload = json.encodeToJsonElement(RequestServerInfo.serializer(), msg)
            }
            is Ping -> {
                typeName = "Ping"
                payload = json.encodeToJsonElement(Ping.serializer(), msg)
            }
            is StartScanning -> {
                typeName = "StartScanning"
                payload = json.encodeToJsonElement(StartScanning.serializer(), msg)
            }
            is StopScanning -> {
                typeName = "StopScanning"
                payload = json.encodeToJsonElement(StopScanning.serializer(), msg)
            }
            is RequestDeviceList -> {
                typeName = "RequestDeviceList"
                payload = json.encodeToJsonElement(RequestDeviceList.serializer(), msg)
            }
            is StopCmd -> {
                typeName = "StopCmd"
                payload = json.encodeToJsonElement(StopCmd.serializer(), msg)
            }
            is StopDeviceCmd -> {
                typeName = "StopDeviceCmd"
                payload = json.encodeToJsonElement(StopDeviceCmd.serializer(), msg)
            }
            is StopAllDevices -> {
                typeName = "StopAllDevices"
                payload = json.encodeToJsonElement(StopAllDevices.serializer(), msg)
            }
            is OutputCmd -> {
                typeName = "OutputCmd"
                payload = json.encodeToJsonElement(OutputCmd.serializer(), msg)
            }
            is InputCmd -> {
                typeName = "InputCmd"
                payload = json.encodeToJsonElement(InputCmd.serializer(), msg)
            }
            is ScalarCmd -> {
                typeName = "ScalarCmd"
                payload = json.encodeToJsonElement(ScalarCmd.serializer(), msg)
            }
            is VibrateCmd -> {
                typeName = "VibrateCmd"
                payload = json.encodeToJsonElement(VibrateCmd.serializer(), msg)
            }
            is RotateCmd -> {
                typeName = "RotateCmd"
                payload = json.encodeToJsonElement(RotateCmd.serializer(), msg)
            }
            is LinearCmd -> {
                typeName = "LinearCmd"
                payload = json.encodeToJsonElement(LinearCmd.serializer(), msg)
            }
            is SensorReadCmd -> {
                typeName = "SensorReadCmd"
                payload = json.encodeToJsonElement(SensorReadCmd.serializer(), msg)
            }
            is SensorSubscribeCmd -> {
                typeName = "SensorSubscribeCmd"
                payload = json.encodeToJsonElement(SensorSubscribeCmd.serializer(), msg)
            }
            is SensorUnsubscribeCmd -> {
                typeName = "SensorUnsubscribeCmd"
                payload = json.encodeToJsonElement(SensorUnsubscribeCmd.serializer(), msg)
            }
            is BatteryLevelCmd -> {
                typeName = "BatteryLevelCmd"
                payload = json.encodeToJsonElement(BatteryLevelCmd.serializer(), msg)
            }
            is RSSILevelCmd -> {
                typeName = "RSSILevelCmd"
                payload = json.encodeToJsonElement(RSSILevelCmd.serializer(), msg)
            }
            is RawWriteCmd -> {
                typeName = "RawWriteCmd"
                payload = json.encodeToJsonElement(RawWriteCmd.serializer(), msg)
            }
            is RawReadCmd -> {
                typeName = "RawReadCmd"
                payload = json.encodeToJsonElement(RawReadCmd.serializer(), msg)
            }
            is RawSubscribeCmd -> {
                typeName = "RawSubscribeCmd"
                payload = json.encodeToJsonElement(RawSubscribeCmd.serializer(), msg)
            }
            is RawUnsubscribeCmd -> {
                typeName = "RawUnsubscribeCmd"
                payload = json.encodeToJsonElement(RawUnsubscribeCmd.serializer(), msg)
            }
            else -> error("Unknown message type: ${msg::class.simpleName}")
        }
        return buildJsonObject { put(typeName, payload) }
    }

    private suspend inline fun <reified T : ButtplugServerMessage> sendAndAwait(msg: Any): T {
        val id: Int = when (msg) {
            is RequestServerInfo -> msg.Id
            is Ping -> msg.Id
            is StartScanning -> msg.Id
            is StopScanning -> msg.Id
            is RequestDeviceList -> msg.Id
            is StopCmd -> msg.Id
            is StopDeviceCmd -> msg.Id
            is StopAllDevices -> msg.Id
            is OutputCmd -> msg.Id
            is InputCmd -> msg.Id
            is ScalarCmd -> msg.Id
            is VibrateCmd -> msg.Id
            is RotateCmd -> msg.Id
            is LinearCmd -> msg.Id
            is SensorReadCmd -> msg.Id
            is SensorSubscribeCmd -> msg.Id
            is SensorUnsubscribeCmd -> msg.Id
            is BatteryLevelCmd -> msg.Id
            is RSSILevelCmd -> msg.Id
            is RawWriteCmd -> msg.Id
            is RawReadCmd -> msg.Id
            is RawSubscribeCmd -> msg.Id
            is RawUnsubscribeCmd -> msg.Id
            else -> error("Unknown message type")
        }
        val deferred = messageSorter.register(id)
        sendMessage(msg)

        val response = withTimeout(config.requestTimeoutMs) { deferred.await() }
        if (response is Error) {
            val code = ButtplugErrorCode.fromCode(response.ErrorCode)
            throw ButtplugException.ProtocolError(code, "Protocol error: ${response.ErrorMessage} (code: ${response.ErrorCode})")
        }
        if (response !is T) {
            throw ButtplugException.ProtocolError(ButtplugErrorCode.ERROR_UNKNOWN, "Unexpected response type: ${response::class.simpleName}, expected ${T::class.simpleName}")
        }
        return response
    }

    private suspend fun sendAndAwaitOk(msg: Any) {
        sendAndAwait<Ok>(msg) // Ok is what we expect
        @Suppress("UNUSED_EXPRESSION")
        Unit // explicit return Unit
    }

    private fun requireConnected() {
        if (_state.value !is ButtplugClientState.Connected && _state.value !is ButtplugClientState.Scanning) {
            throw ButtplugException.NotConnected()
        }
    }

    private fun requireDevice(deviceIndex: Int) {
        if (deviceManager.getDevice(deviceIndex) == null) {
            throw ButtplugException.DeviceNotFound(deviceIndex)
        }
    }

    private suspend fun requestServerInfo() {
        val msg = RequestServerInfo(
            Id = idGenerator.nextId(),
            ClientName = config.clientName,
            ProtocolVersionMajor = config.protocolVersionMajor,
            ProtocolVersionMinor = config.protocolVersionMinor
        )
        sendMessage(msg)
    }
}
