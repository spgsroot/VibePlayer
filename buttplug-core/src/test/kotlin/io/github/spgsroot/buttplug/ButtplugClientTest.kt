package io.github.spgsroot.buttplug

import io.github.spgsroot.buttplug.device.ButtplugDevice
import io.github.spgsroot.buttplug.connection.ButtplugTransport
import io.github.spgsroot.buttplug.connection.TransportListener
import io.github.spgsroot.buttplug.protocol.ButtplugErrorCode
import io.github.spgsroot.buttplug.protocol.ButtplugServerMessage
import io.github.spgsroot.buttplug.protocol.MessageIdGenerator
import io.github.spgsroot.buttplug.protocol.MessageSorter
import io.github.spgsroot.buttplug.protocol.ServerInfo
import io.github.spgsroot.buttplug.device.DeviceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ButtplugClientTest {

    // =========================================================================
    // JSON (same config as ButtplugClient)
    // =========================================================================

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    // =========================================================================
    // Shared state for mock server behavior
    // =========================================================================

    class MockServerState {
        /** The OkHttp WebSocket to the client (set by mock server listener onOpen) */
        var webSocket: WebSocket? = null

        /** Latch released when WebSocket opens */
        val openLatch = CountDownLatch(1)

        /** Latch released when WebSocket closes */
        val closeLatch = CountDownLatch(1)

        /** Count of messages received */
        @Volatile var messageCount = 0

        /** Last received raw text */
        @Volatile var lastReceived: String? = null

        /** Stored message key and payload from last received message */
        @Volatile var lastMessageKey: String? = null
        @Volatile var lastMessagePayload: JsonObject? = null

        /** Configuration for responses */
        var shouldSendErrorOnHandshake: Boolean = false
        var shouldFailHandshake: Boolean = false
        var handshakeErrorCode: Int = ButtplugErrorCode.ERROR_UNKNOWN.code
        var handshakeErrorMessage: String = "Mock handshake error"

        var devicesToReturn: JsonArray = JsonArray(emptyList())
        var batteryLevel: Int = 85
        var rssiLevel: Int = -42

        /** Whether to send Error for the next command */
        var sendErrorForNextCommand: Boolean = false
        var nextCommandErrorCode: Int = ButtplugErrorCode.ERROR_DEVICE.code
        var nextCommandErrorMessage: String = "Simulated device error"

        /** Whether to not respond at all to commands (simulate hang) */
        var shouldNotRespondToCommands: Boolean = false

        /** Close code sent to client */
        @Volatile var closeCode: Int = -1
        @Volatile var closeReason: String = ""
    }

    // =========================================================================
    // Mock WebSocket Listener (server side)
    // =========================================================================

    private fun createMockServerListener(state: MockServerState): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                state.webSocket = webSocket
                state.openLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                state.lastReceived = text
                state.messageCount++

                val element = json.parseToJsonElement(text)
                val messages = when (element) {
                    is JsonArray -> element
                    is JsonObject -> JsonArray(listOf(element))
                    else -> return
                }

                for (msg in messages) {
                    if (msg !is JsonObject) continue
                    val msgObj = msg.jsonObject
                    val typeName = msgObj.keys.firstOrNull() ?: continue
                    val payload = msgObj[typeName]?.jsonObject ?: continue

                    state.lastMessageKey = typeName
                    state.lastMessagePayload = payload

                    handleServerMessage(webSocket, typeName, payload, state)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                state.closeCode = code
                state.closeReason = reason
                state.closeLatch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                // Client disconnected
            }
        }
    }

    private fun handleServerMessage(
        webSocket: WebSocket,
        typeName: String,
        payload: JsonObject,
        state: MockServerState
    ) {
        val id = payload["Id"]?.jsonPrimitive?.intOrNull ?: 0

        when (typeName) {
            "RequestServerInfo" -> {
                if (state.shouldFailHandshake) {
                    // Don't respond at all → triggers timeout in client
                    return
                }
                if (state.shouldSendErrorOnHandshake) {
                    sendJson(webSocket, """{"Error":{"Id":$id,"ErrorCode":${state.handshakeErrorCode},"ErrorMessage":"${state.handshakeErrorMessage}"}}""")
                    return
                }
                sendJson(webSocket, """{"ServerInfo":{"Id":$id,"ServerName":"Mock Buttplug Server","ProtocolVersionMajor":4,"ProtocolVersionMinor":0,"MaxPingTime":100}}""")
            }
            "RequestDeviceList" -> {
                sendJson(webSocket, """{"DeviceList":{"Id":$id,"Devices":${state.devicesToReturn}}}""")
            }
            "StartScanning", "StopScanning", "Ping" -> {
                sendJson(webSocket, """{"Ok":{"Id":$id}}""")
            }
            "OutputCmd", "StopCmd", "StopDeviceCmd", "StopAllDevices" -> {
                if (state.shouldNotRespondToCommands) {
                    // Don't respond → client will hang and eventually get Disconnected
                    return
                }
                if (state.sendErrorForNextCommand) {
                    state.sendErrorForNextCommand = false
                    sendJson(webSocket, """{"Error":{"Id":$id,"ErrorCode":${state.nextCommandErrorCode},"ErrorMessage":"${state.nextCommandErrorMessage}"}}""")
                } else {
                    sendJson(webSocket, """{"Ok":{"Id":$id}}""")
                }
            }
            "InputCmd" -> {
                if (state.sendErrorForNextCommand) {
                    state.sendErrorForNextCommand = false
                    sendJson(webSocket, """{"Error":{"Id":$id,"ErrorCode":${state.nextCommandErrorCode},"ErrorMessage":"${state.nextCommandErrorMessage}"}}""")
                    return
                }
                val inputType = payload["Type"]?.jsonPrimitive?.content
                when (inputType) {
                    "Battery" -> {
                        sendJson(webSocket, """{"InputReading":{"Id":$id,"DeviceIndex":0,"FeatureIndex":1,"Reading":{"Battery":{"Value":${state.batteryLevel}}}}}""")
                    }
                    "Rssi" -> {
                        sendJson(webSocket, """{"InputReading":{"Id":$id,"DeviceIndex":0,"FeatureIndex":1,"Reading":{"Rssi":{"Value":${state.rssiLevel}}}}}""")
                    }
                    else -> {
                        sendJson(webSocket, """{"Error":{"Id":$id,"ErrorCode":${ButtplugErrorCode.ERROR_UNKNOWN.code},"ErrorMessage":"Unknown sensor type: $inputType"}}""")
                    }
                }
            }
            else -> {
                // Unknown message type → send generic error
                sendJson(webSocket, """{"Error":{"Id":$id,"ErrorCode":${ButtplugErrorCode.ERROR_MESSAGE.code},"ErrorMessage":"Unknown message type: $typeName"}}""")
            }
        }
    }

    private fun sendJson(webSocket: WebSocket, jsonString: String) {
        webSocket.send("[$jsonString]")
    }

    // =========================================================================
    // Test fixture
    // =========================================================================

    private lateinit var mockServer: MockWebServer
    private lateinit var mockState: MockServerState
    private lateinit var serverUrl: String

    @BeforeTest
    fun setUp() {
        mockServer = MockWebServer()
        mockState = MockServerState()
    }

    @AfterTest
    fun tearDown() {
        runCatching {
            mockServer.shutdown()
        }
    }

    /** Enqueue the WebSocket upgrade mock response and start the server */
    private fun startMockServer() {
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(createMockServerListener(mockState)))
        serverUrl = mockServer.url("/").toString().replace("http", "ws")
    }

    /** Create a ButtplugClient pointed at the mock server */
    private fun createClient(
        config: ButtplugClientConfig = ButtplugClientConfig(
            serverUrl = serverUrl,
            clientName = "TestClient",
            requestTimeoutMs = 5000L,
            pingIntervalMs = 5000L,
            maxReconnectAttempts = 0 // disable reconnect for tests
        )
    ): ButtplugClient = ButtplugClient(config)

    /** Set up mock devices with a vibrator feature at index 0 and battery sensor at index 1 */
    private fun setupDefaultDevices() {
        mockState.devicesToReturn = JsonArray(
            listOf(
                buildJsonObject {
                    put("DeviceName", JsonPrimitive("Test Device"))
                    put("DeviceIndex", JsonPrimitive(0))
                    put("DeviceDisplayName", JsonPrimitive("Test Device Display"))
                    put("DeviceMessageTimingGap", JsonPrimitive(0))
                    put("DeviceFeatures", buildJsonObject {
                        put("0", buildJsonObject {
                            put("FeatureDescription", JsonPrimitive("Vibrator"))
                            put("FeatureIndex", JsonPrimitive(0))
                            put("Output", buildJsonObject {
                                put("Vibrate", buildJsonObject {
                                    put("Value", JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(20))))
                                })
                            })
                        })
                        put("1", buildJsonObject {
                            put("FeatureDescription", JsonPrimitive("Battery Sensor"))
                            put("FeatureIndex", JsonPrimitive(1))
                            put("Input", buildJsonObject {
                                put("Battery", buildJsonObject {
                                    put("Command", JsonArray(listOf(JsonPrimitive("Read"))))
                                })
                            })
                        })
                    })
                }
            )
        )
    }

    /** Helper: connect client to mock server, assert success, return connected client */
    private suspend fun connectClient(client: ButtplugClient): ButtplugClient {
        val info = client.connect()
        assertTrue(mockState.openLatch.await(5, TimeUnit.SECONDS), "WebSocket should have opened")
        assertNotNull(info, "ServerInfo should not be null")
        assertEquals("Mock Buttplug Server", info.ServerName)
        return client
    }

    // =========================================================================
    // 1. Connection Lifecycle Tests
    // =========================================================================

    @Test
    fun `connect succeeds and returns ServerInfo`() = runBlocking {
        startMockServer()
        val client = createClient()

        val info = client.connect()

        assertNotNull(info)
        assertEquals("Mock Buttplug Server", info.ServerName)
        assertEquals(4, info.ProtocolVersionMajor)
        assertEquals(0, info.ProtocolVersionMinor)
        assertEquals(100L, info.MaxPingTime)
    }

    @Test
    fun `state transitions Disconnected to Connecting to Handshaking to Connected`() = runBlocking {
        startMockServer()
        val client = createClient()

        val states = mutableListOf<ButtplugClientState>()
        val job = launch {
            client.state.collect { states.add(it) }
        }

        assertEquals(ButtplugClientState.Disconnected, client.state.value)

        client.connect()
        delay(200) // give collector time

        job.cancel()

        assertTrue(
            states.any { it is ButtplugClientState.Connecting },
            "Should have passed through Connecting state, got: $states"
        )
        assertTrue(
            states.any { it is ButtplugClientState.Handshaking },
            "Should have passed through Handshaking state, got: $states"
        )
        assertTrue(
            states.last() is ButtplugClientState.Connected,
            "Final state should be Connected, got: ${states.last()}"
        )
    }

    @Test
    fun `connect fails with invalid URL`() = runBlocking {
        val client = ButtplugClient(
            ButtplugClientConfig(
                serverUrl = "ws://localhost:1", // non-existent port
                clientName = "TestClient",
                requestTimeoutMs = 2000L,
                maxReconnectAttempts = 0
            )
        )

        val exception = assertFailsWith<ButtplugException> {
            client.connect()
        }
        assertTrue(
            exception is ButtplugException.ConnectionFailed || exception is ButtplugException.Timeout,
            "Expected ConnectionFailed or Timeout, got: ${exception::class.simpleName}: ${exception.message}"
        )
    }

    @Test
    fun `connect fails with timeout when server does not respond`() = runBlocking {
        startMockServer()
        mockState.shouldFailHandshake = true // server won't respond to RequestServerInfo

        val client = createClient(
            ButtplugClientConfig(
                serverUrl = serverUrl,
                clientName = "TestClient",
                requestTimeoutMs = 1000L,
                maxReconnectAttempts = 0
            )
        )

        val exception = assertFailsWith<ButtplugException.Timeout> {
            client.connect()
        }
        assertTrue(exception.message?.contains("timed out") == true || exception.message?.contains("Handshake") == true)
    }

    @Test
    fun `disconnect transitions to Disconnected`() = runBlocking {
        startMockServer()
        val client = createClient()
        connectClient(client)

        assertTrue(client.state.value is ButtplugClientState.Connected)

        client.disconnect()

        assertEquals(ButtplugClientState.Disconnected, client.state.value)
    }

    @Test
    fun `double connect returns existing server info`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()

        val info1: ServerInfo = client.connect()
        val info2: ServerInfo = client.connect()

        // Should return the same ServerInfo without re-handshaking
        assertEquals(info1.ServerName, info2.ServerName)
        assertEquals(info1.ProtocolVersionMajor, info2.ProtocolVersionMajor)
        assertEquals(info1.MaxPingTime, info2.MaxPingTime)
    }

    // =========================================================================
    // 2. Device Enumeration Tests
    // =========================================================================

    @Test
    fun `requestDeviceList returns parsed devices`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)

        val devices = client.requestDeviceList()

        assertEquals(1, devices.size)
        val device = devices.first()
        assertEquals(0, device.index)
        assertEquals("Test Device", device.name)
        assertEquals("Test Device Display", device.displayName)
        assertTrue(device.features.isNotEmpty(), "Should have features")
    }

    @Test
    fun `startScanning transitions state to Scanning`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)

        // Need at least one device before scanning can transition to Scanning
        // (the handleDeviceList in the client sets Scanning only if devices exist)
        client.requestDeviceList()

        client.startScanning()

        assertEquals(ButtplugClientState.Scanning, client.state.value)
    }

    @Test
    fun `stopScanning transitions back to Connected`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        client.startScanning()
        assertEquals(ButtplugClientState.Scanning, client.state.value)

        client.stopScanning()
        assertTrue(client.state.value is ButtplugClientState.Connected)
    }

    @Test
    fun `devices StateFlow emits updates`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)

        val flowJob = launch {
            val deviceList = client.devices.first { it.isNotEmpty() }
            assertEquals(1, deviceList.size)
            assertEquals("Test Device", deviceList[0].name)
        }

        client.requestDeviceList()
        flowJob.join()
    }

    // =========================================================================
    // 3. Device Command Tests
    // =========================================================================

    @Test
    fun `sendVibrate sends correct OutputCmd with Vibrate`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        client.sendVibrate(deviceIndex = 0, featureIndex = 0, speed = 0.5)

        assertNotNull(mockState.lastReceived)
        assertNotNull(mockState.lastMessageKey)
        assertEquals("OutputCmd", mockState.lastMessageKey)

        val payload = mockState.lastMessagePayload!!
        assertEquals(0, payload["DeviceIndex"]?.jsonPrimitive?.intOrNull)
        assertEquals(0, payload["FeatureIndex"]?.jsonPrimitive?.intOrNull)

        val command = payload["Command"]?.jsonObject
        val vibrate = command?.get("Vibrate")?.jsonObject
        assertEquals(0.5, vibrate?.get("Value")?.jsonPrimitive?.doubleOrNull)
    }

    @Test
    fun `sendRotate sends correct OutputCmd with Rotate`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        client.sendRotate(deviceIndex = 0, featureIndex = 0, speed = 0.75)

        assertEquals("OutputCmd", mockState.lastMessageKey)
        val command = mockState.lastMessagePayload!!["Command"]?.jsonObject
        val rotate = command?.get("Rotate")?.jsonObject
        assertEquals(0.75, rotate?.get("Value")?.jsonPrimitive?.doubleOrNull)
    }

    @Test
    fun `sendLinear sends HwPositionWithDuration`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        client.sendLinear(deviceIndex = 0, featureIndex = 0, position = 0.5, durationMs = 1000)

        assertEquals("OutputCmd", mockState.lastMessageKey)
        val command = mockState.lastMessagePayload!!["Command"]?.jsonObject
        val hwPos = command?.get("HwPositionWithDuration")?.jsonObject
        assertEquals(50, hwPos?.get("Value")?.jsonPrimitive?.intOrNull) // 0.5 * 100 = 50
        assertEquals(1000, hwPos?.get("Duration")?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun `sendStop stops specific device`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        client.sendStop(deviceIndex = 0)

        assertEquals("StopCmd", mockState.lastMessageKey)
        assertEquals(0, mockState.lastMessagePayload!!["DeviceIndex"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun `stopAllDevices stops everything`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        client.stopAllDevices()

        assertEquals("StopCmd", mockState.lastMessageKey)
        // DeviceIndex should be null (stops all)
        assertNull(mockState.lastMessagePayload!!["DeviceIndex"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun `commands fail when not connected`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        // Don't connect the client
        val client = createClient()

        assertFailsWith<ButtplugException.NotConnected> {
            client.sendVibrate(deviceIndex = 0, featureIndex = 0, speed = 0.5)
        }

        assertFailsWith<ButtplugException.NotConnected> {
            client.sendStop()
        }

        assertFailsWith<ButtplugException.NotConnected> {
            client.stopAllDevices()
        }

        assertFailsWith<ButtplugException.NotConnected> {
            client.ping()
        }
    }

    @Test
    fun `commands fail for unknown device index`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        assertFailsWith<ButtplugException.DeviceNotFound> {
            client.sendVibrate(deviceIndex = 999, featureIndex = 0, speed = 0.5)
        }
    }

    // =========================================================================
    // 4. Sensor Command Tests
    // =========================================================================

    @Test
    fun `readBattery returns battery value`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        mockState.batteryLevel = 92
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        val battery = client.readBattery(deviceIndex = 0, featureIndex = 1)

        assertEquals(92, battery)
    }

    @Test
    fun `readRssi returns RSSI value`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        mockState.rssiLevel = -55
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        val rssi = client.readRssi(deviceIndex = 0, featureIndex = 1)

        assertEquals(-55, rssi)
    }

    @Test
    fun `sensor read fails with appropriate error`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        mockState.sendErrorForNextCommand = true
        mockState.nextCommandErrorCode = ButtplugErrorCode.ERROR_DEVICE.code
        mockState.nextCommandErrorMessage = "Sensor read failed"
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        val exception = assertFailsWith<ButtplugException.ProtocolError> {
            client.readBattery(deviceIndex = 0, featureIndex = 1)
        }
        assertEquals(ButtplugErrorCode.ERROR_DEVICE, exception.errorCode)
        assertTrue(exception.message?.contains("Sensor read failed") == true)
    }

    // =========================================================================
    // 5. Error Handling Tests
    // =========================================================================

    @Test
    fun `server Error response throws ProtocolError`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        mockState.sendErrorForNextCommand = true
        mockState.nextCommandErrorCode = ButtplugErrorCode.ERROR_DEVICE.code
        mockState.nextCommandErrorMessage = "Device error"

        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        val exception = assertFailsWith<ButtplugException.ProtocolError> {
            client.sendVibrate(deviceIndex = 0, featureIndex = 0, speed = 0.5)
        }
        assertEquals(ButtplugErrorCode.ERROR_DEVICE, exception.errorCode)
        assertTrue(exception.message?.contains("Device error") == true)
    }

    @Test
    fun `handshake failure throws HandshakeFailed`() = runBlocking {
        startMockServer()
        mockState.shouldSendErrorOnHandshake = true
        mockState.handshakeErrorCode = ButtplugErrorCode.ERROR_HANDSHAKE.code
        mockState.handshakeErrorMessage = "Handshake rejected"

        val client = createClient(
            ButtplugClientConfig(serverUrl = serverUrl, clientName = "TestClient", requestTimeoutMs = 3000L,
                maxReconnectAttempts = 0)
        )

        // Error during handshake causes HandshakeFailed to be thrown from connect()
        val exception = assertFailsWith<ButtplugException.HandshakeFailed> {
            client.connect()
        }
        assertTrue(exception.message?.contains("Handshake rejected") == true)
    }

    @Test
    fun `disconnect while waiting for response throws Disconnected`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient(
            ButtplugClientConfig(
                serverUrl = serverUrl,
                clientName = "TestClient",
                requestTimeoutMs = 5000L,
                maxReconnectAttempts = 0
            )
        )
        connectClient(client)
        client.requestDeviceList()

        // Configure mock to NOT respond, so client hangs waiting for response
        mockState.shouldNotRespondToCommands = true

        // Simulate: mock server closes the connection while we're waiting
        val deferred = async {
            try {
                client.sendVibrate(deviceIndex = 0, featureIndex = 0, speed = 0.5)
                fail("Should have thrown")
            } catch (e: ButtplugException.Disconnected) {
                // expected
            } catch (e: ButtplugException) {
                // Also acceptable - disconnect during pending request can manifest as Disconnected
                assertTrue(e is ButtplugException.Disconnected || e.cause is ButtplugException.Disconnected)
            }
        }

        delay(50) // Let client send the message
        mockState.webSocket?.close(1001, "Server shutdown")

        deferred.await()
    }

    // =========================================================================
    // 6. Ping Tests
    // =========================================================================

    @Test
    fun `ping sends Ping and receives Ok`() = runBlocking {
        startMockServer()
        setupDefaultDevices()
        val client = createClient()
        connectClient(client)
        client.requestDeviceList()

        // Reset message count to isolate ping
        val countBefore = mockState.messageCount
        client.ping()

        assertTrue(mockState.messageCount > countBefore, "Should have sent a ping message")
        assertEquals("Ping", mockState.lastMessageKey)
    }

    @Test
    fun `automatic ping loop runs at correct interval`() = runBlocking {
        startMockServer()
        val client = createClient(
            ButtplugClientConfig(
                serverUrl = serverUrl,
                clientName = "TestClient",
                requestTimeoutMs = 5000L,
                pingIntervalMs = 5000L,
                maxReconnectAttempts = 0
            )
        )
        connectClient(client)

        // The server's MaxPingTime is 100ms, so ping interval = 100/2 = 100ms
        // Wait for a ping to fire
        val initialCount = mockState.messageCount
        delay(300) // Allow at least one ping to fire

        assertTrue(
            mockState.messageCount > initialCount,
            "Automatic ping should have fired. Initial: $initialCount, Final: ${mockState.messageCount}"
        )
    }

    // =========================================================================
    // 7. ButtplugClientConfig Tests
    // =========================================================================

    @Test
    fun `default config is valid`() {
        val config = ButtplugClientConfig()
        assertEquals("ws://localhost:12345", config.serverUrl)
        assertEquals("KotlinButtplugClient", config.clientName)
        assertEquals(4, config.protocolVersionMajor)
    }

    @Test
    fun `blank serverUrl throws`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(serverUrl = "")
        }
        assertTrue(exception.message?.contains("serverUrl") == true)
    }

    @Test
    fun `blank clientName throws`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(clientName = "")
        }
        assertTrue(exception.message?.contains("clientName") == true)
    }

    // =========================================================================
    // Additional: state flow verification
    // =========================================================================

    @Test
    fun `state is Disconnected before connect`() = runBlocking {
        startMockServer()
        val client = createClient()

        assertEquals(ButtplugClientState.Disconnected, client.state.value)
    }

    @Test
    fun `state is Connected after successful handshake`() = runBlocking {
        startMockServer()
        val client = createClient()
        connectClient(client)

        val state = client.state.value
        assertTrue(state is ButtplugClientState.Connected)
        val connected = state as ButtplugClientState.Connected
        assertEquals("Mock Buttplug Server", connected.serverName)
        assertEquals(4, connected.protocolVersionMajor)
        assertEquals(0, connected.protocolVersionMinor)
    }
}
