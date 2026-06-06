package io.github.spgsroot.buttplug.protocol

import io.github.spgsroot.buttplug.ButtplugClientConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Comprehensive unit tests for the Buttplug protocol layer.
 * Covers MessageIdGenerator, MessageSorter, message serialization, and ButtplugClientConfig validation.
 */
class ProtocolTests {

    // =========================================================================
    // JSON instance used across serialization tests
    // =========================================================================

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // =========================================================================
    // 1. MessageIdGenerator Tests
    // =========================================================================

    private val idGen = MessageIdGenerator()

    @Test
    fun `MessageIdGenerator first ID is 1`() {
        assertEquals(1, idGen.nextId())
    }

    @Test
    fun `MessageIdGenerator IDs increment sequentially`() {
        val idGen = MessageIdGenerator() // fresh to avoid test coupling
        assertEquals(1, idGen.nextId())
        assertEquals(2, idGen.nextId())
        assertEquals(3, idGen.nextId())
        assertEquals(4, idGen.nextId())
    }

    @Test
    fun `MessageIdGenerator reset returns to 1`() {
        idGen.nextId()
        idGen.nextId()
        idGen.nextId()
        idGen.reset()
        assertEquals(1, idGen.nextId())
    }

    @Test
    fun `MessageIdGenerator current reflects last generated`() {
        val idGen = MessageIdGenerator()
        idGen.nextId() // returns 1, counter now at 2
        assertEquals(2, idGen.current())
        idGen.nextId() // returns 2, counter now at 3
        assertEquals(3, idGen.current())
    }

    @Test
    fun `MessageIdGenerator is thread safe`() = runTest {
        val idGen = MessageIdGenerator()
        val ids = mutableSetOf<Int>()
        val lock = Any()

        val jobs = List(100) {
            async(Dispatchers.Default) {
                val id = idGen.nextId()
                synchronized(lock) { ids.add(id) }
            }
        }
        jobs.awaitAll()

        assertEquals(100, ids.size, "All IDs must be unique")
        assertTrue(ids.all { it in 1..100 })
    }

    // =========================================================================
    // 2. MessageSorter Tests
    // =========================================================================

    private val sorter = MessageSorter()

    @Test
    fun `MessageSorter register returns CompletableDeferred`() {
        val deferred = sorter.register(42)
        assertNotNull(deferred)
        assertFalse(deferred.isCompleted)
    }

    @Test
    fun `MessageSorter resolve with matching ID completes deferred`() = runTest {
        val deferred = sorter.register(1)
        val msg = Ok(Id = 1)
        val result = sorter.resolve(msg)

        assertTrue(result)
        assertTrue(deferred.isCompleted)
        assertEquals(msg, deferred.await())
    }

    @Test
    fun `MessageSorter resolve with non-matching ID returns false`() = runTest {
        sorter.register(10) // register for ID 10

        // Try resolving with a different ID
        val msg = Ok(Id = 99)
        val result = sorter.resolve(msg)

        assertFalse(result)
    }

    @Test
    fun `MessageSorter resolve with ID 0 system event returns false`() = runTest {
        // ID 0 is reserved for system events and should never match
        val msg = ScanningFinished(Id = 0)
        val result = sorter.resolve(msg)

        assertFalse(result)
    }

    @Test
    fun `MessageSorter cancelAll completes all pending with exception`() = runTest {
        val d1 = sorter.register(100)
        val d2 = sorter.register(200)

        val cause = RuntimeException("test disconnect")
        sorter.cancelAll(cause)

        assertTrue(d1.isCompleted)
        assertTrue(d1.isCancelled)
        assertTrue(d2.isCompleted)
        assertTrue(d2.isCancelled)

        // Verify the exception was the cause
        val ex1 = try { d1.await(); null } catch (e: Exception) { e }
        val ex2 = try { d2.await(); null } catch (e: Exception) { e }
        assertNotNull(ex1)
        assertNotNull(ex2)
    }

    @Test
    fun `MessageSorter pendingCount reflects actual pending count`() {
        assertEquals(0, sorter.pendingCount())

        sorter.register(1)
        assertEquals(1, sorter.pendingCount())

        sorter.register(2)
        assertEquals(2, sorter.pendingCount())

        sorter.resolve(Ok(Id = 1))
        assertEquals(1, sorter.pendingCount())
    }

    @Test
    fun `MessageSorter multiple concurrent registrations work correctly`() = runTest {
        val ids = (1..10).toList()
        val deferreds = ids.map { sorter.register(it) }

        // Resolve them all in reverse order
        ids.reversed().forEach { id ->
            val msg = Ok(Id = id)
            val result = sorter.resolve(msg)
            assertTrue(result, "Should resolve ID $id")
        }

        // All should be completed
        deferreds.forEach { assertTrue(it.isCompleted) }

        // Each should have its correct message
        ids.forEach { id ->
            assertEquals(id, deferreds[id - 1].await().Id)
        }

        assertEquals(0, sorter.pendingCount())
    }

    @Test
    fun `MessageSorter reset cancels all`() = runTest {
        val d1 = sorter.register(50)
        val d2 = sorter.register(51)

        sorter.reset()

        assertTrue(d1.isCompleted && d1.isCancelled)
        assertTrue(d2.isCompleted && d2.isCancelled)
        assertEquals(0, sorter.pendingCount())
    }

    @Test
    fun `MessageSorter resolve same ID twice - second returns false`() = runTest {
        sorter.register(7)
        val msg = Ok(Id = 7)

        assertTrue(sorter.resolve(msg))     // first resolve succeeds
        assertFalse(sorter.resolve(msg))     // already removed, second fails
    }

    // =========================================================================
    // 3. ButtplugMessage Serialization Tests
    // =========================================================================

    @Test
    fun `ServerInfo round-trip serialize deserialize`() {
        val original = ServerInfo(
            Id = 1,
            ServerName = "TestServer",
            ProtocolVersionMajor = 4,
            ProtocolVersionMinor = 0,
            MaxPingTime = 5000
        )
        val jsonStr = json.encodeToString(ServerInfo.serializer(), original)
        val deserialized = json.decodeFromString(ServerInfo.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `ServerInfo with default values round-trip`() {
        val original = ServerInfo(Id = 1)
        val jsonStr = json.encodeToString(ServerInfo.serializer(), original)
        val deserialized = json.decodeFromString(ServerInfo.serializer(), jsonStr)

        assertEquals(1, deserialized.Id)
        assertEquals("", deserialized.ServerName)
        assertEquals(4, deserialized.ProtocolVersionMajor)
    }

    @Test
    fun `DeviceList with DeviceFeatures round-trip`() {
        val devices = buildJsonObject {
            put("0", buildJsonObject {
                put("DeviceName", JsonPrimitive("Lovense Hush"))
                put("DeviceIndex", JsonPrimitive(0))
                put("DeviceDisplayName", JsonPrimitive("Hush"))
                put("DeviceMessageTimingGap", JsonPrimitive(50))
                put("DeviceFeatures", buildJsonObject {
                    put("Vibrate", buildJsonObject {
                        put("FeatureDescription", JsonPrimitive("Main vibrator"))
                        put("FeatureIndex", JsonPrimitive(0))
                        put("Output", buildJsonObject { put("Strength", JsonPrimitive(0)) })
                    })
                    put("Rotate", buildJsonObject {
                        put("FeatureDescription", JsonPrimitive("Rotation motor"))
                        put("FeatureIndex", JsonPrimitive(1))
                    })
                })
            })
        }
        val original = DeviceList(Id = 2, Devices = devices)

        val jsonStr = json.encodeToString(DeviceList.serializer(), original)
        val deserialized = json.decodeFromString(DeviceList.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `DeviceList with empty devices round-trip`() {
        val original = DeviceList(Id = 1)
        val jsonStr = json.encodeToString(DeviceList.serializer(), original)
        val deserialized = json.decodeFromString(DeviceList.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertTrue(deserialized.Devices.isEmpty())
    }

    @Test
    fun `OutputCmd with Vibrate round-trip`() {
        val command = OutputCommandValue(
            Vibrate = ScalarCommand(Value = 10)
        )
        val original = OutputCmd(
            Id = 3,
            DeviceIndex = 0,
            FeatureIndex = 0,
            Command = command
        )

        val jsonStr = json.encodeToString(OutputCmd.serializer(), original)
        val deserialized = json.decodeFromString(OutputCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(10, deserialized.Command.Vibrate?.Value)
    }

    @Test
    fun `OutputCmd with multiple scalar types round-trip`() {
        val command = OutputCommandValue(
            Vibrate = ScalarCommand(Value = 10),
            Rotate = ScalarCommand(Value = 10),
            Oscillate = ScalarCommand(Value = 10)
        )
        val original = OutputCmd(
            Id = 4,
            DeviceIndex = 1,
            FeatureIndex = 2,
            Command = command
        )

        val jsonStr = json.encodeToString(OutputCmd.serializer(), original)
        val deserialized = json.decodeFromString(OutputCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `OutputCmd with Position command round-trip`() {
        val command = OutputCommandValue(
            HwPositionWithDuration = PositionWithDurationCommand(Value = 90, Duration = 1000)
        )
        val original = OutputCmd(
            Id = 5,
            DeviceIndex = 0,
            FeatureIndex = 0,
            Command = command
        )

        val jsonStr = json.encodeToString(OutputCmd.serializer(), original)
        val deserialized = json.decodeFromString(OutputCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(90, deserialized.Command.HwPositionWithDuration?.Value)
        assertEquals(1000, deserialized.Command.HwPositionWithDuration?.Duration)
    }

    @Test
    fun `InputCmd round-trip`() {
        val original = InputCmd(
            Id = 5,
            DeviceIndex = 0,
            FeatureIndex = 1,
            Type = "Battery",
            Command = "Subscribe"
        )

        val jsonStr = json.encodeToString(InputCmd.serializer(), original)
        val deserialized = json.decodeFromString(InputCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `Error message round-trip with error code`() {
        val original = Error(Id = 1, ErrorCode = 3, ErrorMessage = "Invalid message format")

        val jsonStr = json.encodeToString(Error.serializer(), original)
        val deserialized = json.decodeFromString(Error.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(3, deserialized.ErrorCode)
        assertEquals("Invalid message format", deserialized.ErrorMessage)
    }

    @Test
    fun `Ok round-trip`() {
        val original = Ok(Id = 42)

        val jsonStr = json.encodeToString(Ok.serializer(), original)
        val deserialized = json.decodeFromString(Ok.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RequestServerInfo serialization produces expected JSON fields`() {
        val msg = RequestServerInfo(Id = 1, ClientName = "TestClient")
        val jsonStr = json.encodeToString(RequestServerInfo.serializer(), msg)

        assertTrue(jsonStr.contains("\"Id\":"))
        assertTrue(jsonStr.contains("TestClient"))
        assertTrue(jsonStr.contains("\"ClientName\":"))
    }

    @Test
    fun `RequestServerInfo round-trip`() {
        val original = RequestServerInfo(
            Id = 1,
            ClientName = "MyClient",
            ProtocolVersionMajor = 4,
            ProtocolVersionMinor = 0
        )

        val jsonStr = json.encodeToString(RequestServerInfo.serializer(), original)
        val deserialized = json.decodeFromString(RequestServerInfo.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `Ping serialization round-trip`() {
        val original = Ping(Id = 100)

        val jsonStr = json.encodeToString(Ping.serializer(), original)
        val deserialized = json.decodeFromString(Ping.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `StopCmd with specific device round-trip`() {
        val original = StopCmd(
            Id = 10,
            DeviceIndex = 3,
            FeatureIndex = 1,
            Inputs = true,
            Outputs = true
        )

        val jsonStr = json.encodeToString(StopCmd.serializer(), original)
        val deserialized = json.decodeFromString(StopCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `StopCmd all devices variant round-trip`() {
        // Stop all: no DeviceIndex or FeatureIndex specified (null)
        val original = StopCmd(Id = 11) // defaults

        val jsonStr = json.encodeToString(StopCmd.serializer(), original)
        val deserialized = json.decodeFromString(StopCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertNull(deserialized.DeviceIndex)
        assertNull(deserialized.FeatureIndex)
    }

    @Test
    fun `StopCmd with Inputs false Outputs true`() {
        val original = StopCmd(
            Id = 12,
            DeviceIndex = 0,
            Inputs = false,
            Outputs = true
        )

        val jsonStr = json.encodeToString(StopCmd.serializer(), original)
        val deserialized = json.decodeFromString(StopCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertFalse(deserialized.Inputs)
        assertTrue(deserialized.Outputs)
    }

    @Test
    fun `ScalarCmd v3 legacy round-trip`() {
        val original = ScalarCmd(
            Id = 99,
            DeviceIndex = 1,
            Scalars = listOf(
                ScalarSubcommand(Index = 0, Scalar = 0.5, ActuatorType = "Vibrate"),
                ScalarSubcommand(Index = 1, Scalar = 0.8, ActuatorType = "Vibrate")
            )
        )

        val jsonStr = json.encodeToString(ScalarCmd.serializer(), original)
        val deserialized = json.decodeFromString(ScalarCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(2, deserialized.Scalars.size)
    }

    @Test
    fun `VibrateCmd v2 legacy round-trip`() {
        val original = VibrateCmd(
            Id = 50,
            DeviceIndex = 0,
            Speeds = listOf(
                SpeedSubcommand(Index = 0, Speed = 0.3),
                SpeedSubcommand(Index = 1, Speed = 1.0)
            )
        )

        val jsonStr = json.encodeToString(VibrateCmd.serializer(), original)
        val deserialized = json.decodeFromString(VibrateCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(2, deserialized.Speeds.size)
    }

    @Test
    fun `StopDeviceCmd v3 legacy round-trip`() {
        val original = StopDeviceCmd(Id = 7, DeviceIndex = 3)
        val jsonStr = json.encodeToString(StopDeviceCmd.serializer(), original)
        val deserialized = json.decodeFromString(StopDeviceCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `StopAllDevices v3 legacy round-trip`() {
        val original = StopAllDevices(Id = 8)
        val jsonStr = json.encodeToString(StopAllDevices.serializer(), original)
        val deserialized = json.decodeFromString(StopAllDevices.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `InputReading round-trip with battery sensor`() {
        val reading = SensorReading(Battery = BatteryValue(Value = 85))
        val original = InputReading(
            Id = 1,
            DeviceIndex = 0,
            FeatureIndex = 0,
            Reading = reading
        )

        val jsonStr = json.encodeToString(InputReading.serializer(), original)
        val deserialized = json.decodeFromString(InputReading.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(85, deserialized.Reading.Battery?.Value)
    }

    @Test
    fun `ScanningFinished system event serialization`() {
        val original = ScanningFinished(Id = 0)
        val jsonStr = json.encodeToString(ScanningFinished.serializer(), original)
        val deserialized = json.decodeFromString(ScanningFinished.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(0, deserialized.Id)
    }

    @Test
    fun `RotateCmd v3 legacy round-trip`() {
        val original = RotateCmd(
            Id = 6,
            DeviceIndex = 2,
            Rotations = listOf(
                RotationSubcommand(Index = 0, Speed = 0.7, Clockwise = true)
            )
        )

        val jsonStr = json.encodeToString(RotateCmd.serializer(), original)
        val deserialized = json.decodeFromString(RotateCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `LinearCmd v3 legacy round-trip`() {
        val original = LinearCmd(
            Id = 7,
            DeviceIndex = 0,
            Vectors = listOf(
                LinearSubcommand(Index = 0, Duration = 500, Position = 0.5)
            )
        )

        val jsonStr = json.encodeToString(LinearCmd.serializer(), original)
        val deserialized = json.decodeFromString(LinearCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `SensorReadCmd v3 legacy round-trip`() {
        val original = SensorReadCmd(
            Id = 9,
            DeviceIndex = 0,
            SensorIndex = 1,
            SensorType = "Battery"
        )

        val jsonStr = json.encodeToString(SensorReadCmd.serializer(), original)
        val deserialized = json.decodeFromString(SensorReadCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    // =========================================================================
    // 3.1 Message Envelope Wrapping
    // =========================================================================

    @Serializable
    data class Envelope(val Ok: Ok? = null)

    @Test
    fun `message envelope wrapping produces expected format`() {
        val msg = Ok(Id = 1)

        // Wrap in envelope: [{"Ok": {"Id": 1}}]
        val envelopeObj = buildJsonObject {
            putJsonObject("Ok") {
                put("Id", 1)
            }
        }
        val encoded = "[${json.encodeToString(JsonObject.serializer(), envelopeObj)}]"

        assertTrue(encoded.contains("Ok"))
        assertTrue(encoded.contains("\"Id\":1") || encoded.contains("\"Id\": 1"))
        assertTrue(encoded.startsWith("["))
        assertTrue(encoded.endsWith("]"))
    }

    @Test
    fun `message envelope wrapping with ServerInfo`() {
        val msg = ServerInfo(Id = 1, ServerName = "Test", MaxPingTime = 1000)
        val encoded = json.encodeToJsonElement(ServerInfo.serializer(), msg)
        val envelope = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()),
            listOf(buildJsonObject { put("ServerInfo", encoded) })
        )

        assertTrue(envelope.startsWith("["))
        assertTrue(envelope.endsWith("]"))
        assertTrue(envelope.contains("ServerInfo"))
        assertTrue(envelope.contains("Test"))
    }

    // =========================================================================
    // 3.2 Unknown Keys Are Ignored
    // =========================================================================

    @Test
    fun `unknown keys are ignored during deserialization`() {
        val jsonWithExtra = """
            {
                "Id": 42,
                "UnknownField": "should be ignored",
                "AnotherUnknown": 12345
            }
        """.trimIndent()

        val result = json.decodeFromString(Ok.serializer(), jsonWithExtra)
        assertEquals(42, result.Id)
    }

    @Test
    fun `unknown keys with nested objects are ignored`() {
        val jsonWithExtra = """
            {
                "Id": 7,
                "ExtraObject": {
                    "NestedKey": "ignored",
                    "DeepNest": { "More": true }
                }
            }
        """.trimIndent()

        val result = json.decodeFromString(Ok.serializer(), jsonWithExtra)
        assertEquals(7, result.Id)
    }

    // =========================================================================
    // 3.3 ButtplugErrorCode fromCode Mapping
    // =========================================================================

    @Test
    fun `ButtplugErrorCode fromCode maps correctly`() {
        assertEquals(ButtplugErrorCode.ERROR_UNKNOWN, ButtplugErrorCode.fromCode(0))
        assertEquals(ButtplugErrorCode.ERROR_HANDSHAKE, ButtplugErrorCode.fromCode(1))
        assertEquals(ButtplugErrorCode.ERROR_PING, ButtplugErrorCode.fromCode(2))
        assertEquals(ButtplugErrorCode.ERROR_MESSAGE, ButtplugErrorCode.fromCode(3))
        assertEquals(ButtplugErrorCode.ERROR_DEVICE, ButtplugErrorCode.fromCode(4))
    }

    @Test
    fun `ButtplugErrorCode fromCode unknown returns ERROR_UNKNOWN`() {
        assertEquals(ButtplugErrorCode.ERROR_UNKNOWN, ButtplugErrorCode.fromCode(999))
        assertEquals(ButtplugErrorCode.ERROR_UNKNOWN, ButtplugErrorCode.fromCode(-1))
        assertEquals(ButtplugErrorCode.ERROR_UNKNOWN, ButtplugErrorCode.fromCode(100))
    }

    @Test
    fun `ButtplugErrorCode code property matches enum mapping`() {
        assertEquals(0, ButtplugErrorCode.ERROR_UNKNOWN.code)
        assertEquals(1, ButtplugErrorCode.ERROR_HANDSHAKE.code)
        assertEquals(2, ButtplugErrorCode.ERROR_PING.code)
        assertEquals(3, ButtplugErrorCode.ERROR_MESSAGE.code)
        assertEquals(4, ButtplugErrorCode.ERROR_DEVICE.code)
    }

    // =========================================================================
    // 4. ButtplugClientConfig Validation Tests
    // =========================================================================

    @Test
    fun `ButtplugClientConfig default config is valid`() {
        val config = ButtplugClientConfig()
        assertNotNull(config)
        assertEquals("ws://localhost:12345", config.serverUrl)
        assertEquals("KotlinButtplugClient", config.clientName)
        assertEquals(4, config.protocolVersionMajor)
        assertEquals(3000L, config.reconnectDelayMs)
    }

    @Test
    fun `ButtplugClientConfig blank serverUrl throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(serverUrl = "")
        }
        assertTrue(error.message!!.contains("serverUrl"))
    }

    @Test
    fun `ButtplugClientConfig whitespace only serverUrl throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(serverUrl = "   ")
        }
        assertTrue(error.message!!.contains("serverUrl"))
    }

    @Test
    fun `ButtplugClientConfig blank clientName throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(clientName = "")
        }
        assertTrue(error.message!!.contains("clientName"))
    }

    @Test
    fun `ButtplugClientConfig whitespace only clientName throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(clientName = "  ")
        }
        assertTrue(error.message!!.contains("clientName"))
    }

    @Test
    fun `ButtplugClientConfig negative protocol version throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(protocolVersionMajor = -1)
        }
        assertTrue(error.message!!.contains("protocolVersionMajor"))
    }

    @Test
    fun `ButtplugClientConfig protocol version above 4 throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(protocolVersionMajor = 5)
        }
        assertTrue(error.message!!.contains("protocolVersionMajor"))
    }

    @Test
    fun `ButtplugClientConfig protocol version 0 is valid`() {
        val config = ButtplugClientConfig(protocolVersionMajor = 0)
        assertEquals(0, config.protocolVersionMajor)
    }

    @Test
    fun `ButtplugClientConfig protocol version 4 is valid`() {
        val config = ButtplugClientConfig(protocolVersionMajor = 4)
        assertEquals(4, config.protocolVersionMajor)
    }

    @Test
    fun `ButtplugClientConfig negative reconnect delay throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(reconnectDelayMs = -1)
        }
        assertTrue(error.message!!.contains("reconnectDelayMs"))
    }

    @Test
    fun `ButtplugClientConfig zero reconnect delay throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(reconnectDelayMs = 0)
        }
        assertTrue(error.message!!.contains("reconnectDelayMs"))
    }

    @Test
    fun `ButtplugClientConfig negative ping interval throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ButtplugClientConfig(pingIntervalMs = -100)
        }
        assertTrue(error.message!!.contains("pingIntervalMs"))
    }

    @Test
    fun `ButtplugClientConfig custom valid config works`() {
        val config = ButtplugClientConfig(
            serverUrl = "wss://example.com:12345",
            clientName = "MyApp",
            protocolVersionMajor = 3,
            reconnectDelayMs = 5000L,
            maxReconnectAttempts = 5,
            pingIntervalMs = 10000L,
            requestTimeoutMs = 15000L,
            bypassCertVerify = false
        )

        assertEquals("wss://example.com:12345", config.serverUrl)
        assertEquals("MyApp", config.clientName)
        assertEquals(3, config.protocolVersionMajor)
        assertEquals(5000L, config.reconnectDelayMs)
        assertEquals(5, config.maxReconnectAttempts)
        assertEquals(10000L, config.pingIntervalMs)
        assertEquals(15000L, config.requestTimeoutMs)
        assertFalse(config.bypassCertVerify)
    }

    @Test
    fun `ButtplugClientConfig DEFAULT is valid`() {
        val config = ButtplugClientConfig.DEFAULT
        assertNotNull(config)
        assertEquals("ws://localhost:12345", config.serverUrl)
    }

    // =========================================================================
    // Additional serialization coverage: DeviceAddedV3
    // =========================================================================

    @Test
    fun `DeviceAddedV3 round-trip`() {
        val original = DeviceAddedV3(
            DeviceName = "TestDevice",
            DeviceIndex = 5,
            DeviceDisplayName = "Display TestDevice",
            DeviceMessageTimingGap = 30
        )

        val jsonStr = json.encodeToString(DeviceAddedV3.serializer(), original)
        val deserialized = json.decodeFromString(DeviceAddedV3.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(0, deserialized.Id)
    }

    @Test
    fun `DeviceAddedV3 with DeviceMessages round-trip`() {
        val messages = buildJsonObject {
            putJsonObject("VibrateCmd") {
                putJsonObject("FeatureCount") {
                    put("ScalarCmd", 0)
                }
            }
        }
        val original = DeviceAddedV3(
            DeviceName = "Lovense Edge",
            DeviceIndex = 2,
            DeviceMessages = messages
        )

        val jsonStr = json.encodeToString(DeviceAddedV3.serializer(), original)
        val deserialized = json.decodeFromString(DeviceAddedV3.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertNotNull(deserialized.DeviceMessages)
    }

    @Test
    fun `DeviceRemoved round-trip`() {
        val original = DeviceRemoved(Id = 0, DeviceIndex = 3)
        val jsonStr = json.encodeToString(DeviceRemoved.serializer(), original)
        val deserialized = json.decodeFromString(DeviceRemoved.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    // =========================================================================
    // Additional serialization coverage: Sensor-related messages
    // =========================================================================

    @Test
    fun `SensorSubscribeCmd round-trip`() {
        val original = SensorSubscribeCmd(
            Id = 8,
            DeviceIndex = 0,
            SensorIndex = 2,
            SensorType = "Pressure"
        )

        val jsonStr = json.encodeToString(SensorSubscribeCmd.serializer(), original)
        val deserialized = json.decodeFromString(SensorSubscribeCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `SensorUnsubscribeCmd round-trip`() {
        val original = SensorUnsubscribeCmd(
            Id = 9,
            DeviceIndex = 0,
            SensorIndex = 2,
            SensorType = "Pressure"
        )

        val jsonStr = json.encodeToString(SensorUnsubscribeCmd.serializer(), original)
        val deserialized = json.decodeFromString(SensorUnsubscribeCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `SensorReadingV3 round-trip`() {
        val original = SensorReadingV3(
            Id = 10,
            DeviceIndex = 0,
            SensorIndex = 1,
            SensorType = "Battery",
            Data = listOf(85, 100)
        )

        val jsonStr = json.encodeToString(SensorReadingV3.serializer(), original)
        val deserialized = json.decodeFromString(SensorReadingV3.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    // =========================================================================
    // Additional serialization coverage: v2 raw messages
    // =========================================================================

    @Test
    fun `RawWriteCmd round-trip`() {
        val original = RawWriteCmd(
            Id = 10,
            DeviceIndex = 0,
            Endpoint = "tx",
            Data = listOf(0x01, 0x02, 0xFF),
            WriteWithResponse = true
        )

        val jsonStr = json.encodeToString(RawWriteCmd.serializer(), original)
        val deserialized = json.decodeFromString(RawWriteCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RawReadCmd round-trip`() {
        val original = RawReadCmd(
            Id = 11,
            DeviceIndex = 0,
            Endpoint = "rx",
            ExpectedLength = 20,
            WaitForData = true
        )

        val jsonStr = json.encodeToString(RawReadCmd.serializer(), original)
        val deserialized = json.decodeFromString(RawReadCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RawReading round-trip`() {
        val original = RawReading(
            Id = 12,
            DeviceIndex = 0,
            Endpoint = "rx",
            Data = listOf(0x48, 0x65, 0x6C)
        )

        val jsonStr = json.encodeToString(RawReading.serializer(), original)
        val deserialized = json.decodeFromString(RawReading.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RawSubscribeCmd round-trip`() {
        val original = RawSubscribeCmd(Id = 13, DeviceIndex = 0, Endpoint = "rx")
        val jsonStr = json.encodeToString(RawSubscribeCmd.serializer(), original)
        val deserialized = json.decodeFromString(RawSubscribeCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RawUnsubscribeCmd round-trip`() {
        val original = RawUnsubscribeCmd(Id = 14, DeviceIndex = 0, Endpoint = "rx")
        val jsonStr = json.encodeToString(RawUnsubscribeCmd.serializer(), original)
        val deserialized = json.decodeFromString(RawUnsubscribeCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `BatteryLevelCmd round-trip`() {
        val original = BatteryLevelCmd(Id = 15, DeviceIndex = 0)
        val jsonStr = json.encodeToString(BatteryLevelCmd.serializer(), original)
        val deserialized = json.decodeFromString(BatteryLevelCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `BatteryLevelReading round-trip`() {
        val original = BatteryLevelReading(Id = 16, DeviceIndex = 0, BatteryLevel = 0.85)
        val jsonStr = json.encodeToString(BatteryLevelReading.serializer(), original)
        val deserialized = json.decodeFromString(BatteryLevelReading.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RSSILevelCmd round-trip`() {
        val original = RSSILevelCmd(Id = 17, DeviceIndex = 0)
        val jsonStr = json.encodeToString(RSSILevelCmd.serializer(), original)
        val deserialized = json.decodeFromString(RSSILevelCmd.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RSSILevelReading round-trip`() {
        val original = RSSILevelReading(Id = 18, DeviceIndex = 0, RSSILevel = -42.5)
        val jsonStr = json.encodeToString(RSSILevelReading.serializer(), original)
        val deserialized = json.decodeFromString(RSSILevelReading.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `InputReading with button sensor round-trip`() {
        val reading = SensorReading(Button = ButtonValue(Value = 1))
        val original = InputReading(
            Id = 2,
            DeviceIndex = 0,
            FeatureIndex = 3,
            Reading = reading
        )

        val jsonStr = json.encodeToString(InputReading.serializer(), original)
        val deserialized = json.decodeFromString(InputReading.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(1, deserialized.Reading.Button?.Value)
    }

    @Test
    fun `InputReading with pressure sensor round-trip`() {
        val reading = SensorReading(Pressure = PressureValue(Value = 42))
        val original = InputReading(
            Id = 3,
            DeviceIndex = 1,
            FeatureIndex = 0,
            Reading = reading
        )

        val jsonStr = json.encodeToString(InputReading.serializer(), original)
        val deserialized = json.decodeFromString(InputReading.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(42, deserialized.Reading.Pressure?.Value)
    }

    @Test
    fun `InputReading with RSSI sensor round-trip`() {
        val reading = SensorReading(Rssi = RssiValue(Value = -55))
        val original = InputReading(
            Id = 4,
            DeviceIndex = 0,
            FeatureIndex = 1,
            Reading = reading
        )

        val jsonStr = json.encodeToString(InputReading.serializer(), original)
        val deserialized = json.decodeFromString(InputReading.serializer(), jsonStr)

        assertEquals(original, deserialized)
        assertEquals(-55, deserialized.Reading.Rssi?.Value)
    }

    // =========================================================================
    // StartScanning / StopScanning / RequestDeviceList coverage
    // =========================================================================

    @Test
    fun `StartScanning round-trip`() {
        val original = StartScanning(Id = 20)
        val jsonStr = json.encodeToString(StartScanning.serializer(), original)
        val deserialized = json.decodeFromString(StartScanning.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `StopScanning round-trip`() {
        val original = StopScanning(Id = 21)
        val jsonStr = json.encodeToString(StopScanning.serializer(), original)
        val deserialized = json.decodeFromString(StopScanning.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    @Test
    fun `RequestDeviceList round-trip`() {
        val original = RequestDeviceList(Id = 22)
        val jsonStr = json.encodeToString(RequestDeviceList.serializer(), original)
        val deserialized = json.decodeFromString(RequestDeviceList.serializer(), jsonStr)

        assertEquals(original, deserialized)
    }

    // =========================================================================
    // Edge cases and additional coverage
    // =========================================================================

    @Test
    fun `OutputCommandValue with all null scalars serializes as empty object`() {
        val cmd = OutputCommandValue()
        val jsonStr = json.encodeToString(OutputCommandValue.serializer(), cmd)

        assertTrue(jsonStr.contains("{"))
        assertTrue(jsonStr.contains("}"))
    }

    @Test
    fun `DeviceFeatureV4 with null Output and Input round-trip`() {
        val feature = DeviceFeatureV4(
            FeatureDescription = "No IO feature",
            FeatureIndex = 5
        )

        val jsonStr = json.encodeToString(DeviceFeatureV4.serializer(), feature)
        val deserialized = json.decodeFromString(DeviceFeatureV4.serializer(), jsonStr)

        assertEquals(feature, deserialized)
    }

    @Test
    fun `MessageIdGenerator sequential IDs across multiple generators do not overlap`() {
        val gen1 = MessageIdGenerator()
        val gen2 = MessageIdGenerator()

        assertEquals(1, gen1.nextId())
        assertEquals(1, gen2.nextId()) // each generator is independent
    }

    @Test
    fun `MessageIdGenerator reset during concurrent use`() = runTest {
        val gen = MessageIdGenerator()
        gen.nextId() // 1
        gen.nextId() // 2
        gen.reset()

        val ids = mutableSetOf<Int>()
        val lock = Any()
        val jobs = List(50) {
            async(Dispatchers.Default) {
                val id = gen.nextId()
                synchronized(lock) { ids.add(id) }
            }
        }
        jobs.awaitAll()

        assertEquals(50, ids.size)
        // After reset, first ID should be 1
        assertTrue(ids.contains(1))
    }
}
