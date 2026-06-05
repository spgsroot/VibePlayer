@file:Suppress("PropertyName", "unused")

package io.github.spgsroot.buttplug.protocol

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Complete Buttplug protocol message hierarchy with 100% spec coverage.
 *
 * Messages are always transmitted as JSON arrays: [{"MessageType": {"Id": 1, ...}}]
 * The outer array wrapper is handled by the transport layer.
 *
 * Id=0 reserved for server-to-client system events.
 * Id>=1 for client requests and their server responses.
 */
@Serializable
sealed interface ButtplugServerMessage {
    val Id: Int
}

// =============================================================================
// SYSTEM EVENTS (Id = 0, server -> client)
// =============================================================================

@Serializable
@SerialName("ScanningFinished")
data class ScanningFinished(
    override val Id: Int = 0
) : ButtplugServerMessage

@Serializable
@SerialName("DeviceAdded")
data class DeviceAddedV3(
    val DeviceName: String,
    val DeviceIndex: Int,
    val DeviceDisplayName: String = DeviceName,
    val DeviceMessageTimingGap: Long = 0,
    val DeviceMessages: JsonObject? = null
) {
    val Id: Int get() = 0
}

@Serializable
@SerialName("DeviceRemoved")
data class DeviceRemoved(
    override val Id: Int = 0,
    val DeviceIndex: Int
) : ButtplugServerMessage

// =============================================================================
// HANDSHAKE MESSAGES
// =============================================================================

@Serializable
@SerialName("RequestServerInfo")
data class RequestServerInfo(
    val Id: Int,
    val ClientName: String,
    val ProtocolVersionMajor: Int = 4,
    val ProtocolVersionMinor: Int = 0
)

@Serializable
@SerialName("ServerInfo")
data class ServerInfo(
    override val Id: Int,
    val ServerName: String = "",
    val ProtocolVersionMajor: Int = 4,
    val ProtocolVersionMinor: Int = 0,
    val MaxPingTime: Long = 0
) : ButtplugServerMessage

// =============================================================================
// PING
// =============================================================================

@Serializable
@SerialName("Ping")
data class Ping(val Id: Int)

// =============================================================================
// STATUS RESPONSE
// =============================================================================

@Serializable
@SerialName("Ok")
data class Ok(
    override val Id: Int
) : ButtplugServerMessage

@Serializable
@SerialName("Error")
data class Error(
    override val Id: Int,
    val ErrorCode: Int,
    val ErrorMessage: String
) : ButtplugServerMessage

// =============================================================================
// DEVICE ENUMERATION
// =============================================================================

@Serializable
@SerialName("StartScanning")
data class StartScanning(val Id: Int)

@Serializable
@SerialName("StopScanning")
data class StopScanning(val Id: Int)

@Serializable
@SerialName("RequestDeviceList")
data class RequestDeviceList(val Id: Int)

// =============================================================================
// DEVICE LIST (v4 format - uses DeviceFeatures map)
// =============================================================================

@Serializable
data class DeviceInfoV4(
    val DeviceName: String,
    val DeviceIndex: Int,
    val DeviceDisplayName: String = DeviceName,
    val DeviceMessageTimingGap: Long = 0,
    val DeviceFeatures: Map<String, DeviceFeatureV4> = emptyMap()
)

@Serializable
data class DeviceFeatureV4(
    val FeatureDescription: String = "",
    val FeatureIndex: Int = 0,
    val Output: JsonObject? = null,
    val Input: JsonObject? = null
)

@Serializable
@SerialName("DeviceList")
data class DeviceList(
    override val Id: Int,
    val Devices: List<DeviceInfoV4> = emptyList()
) : ButtplugServerMessage

// =============================================================================
// DEVICE COMMANDS (v4)
// =============================================================================

@Serializable
@SerialName("StopCmd")
data class StopCmd(
    val Id: Int,
    val DeviceIndex: Int? = null,
    val FeatureIndex: Int? = null,
    val Inputs: Boolean = true,
    val Outputs: Boolean = true
)

@Serializable
@SerialName("OutputCmd")
data class OutputCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val FeatureIndex: Int,
    val Command: OutputCommandValue
)

@Serializable
data class OutputCommandValue(
    @EncodeDefault val Vibrate: ScalarCommand? = null,
    @EncodeDefault val Rotate: ScalarCommand? = null,
    @EncodeDefault val Oscillate: ScalarCommand? = null,
    @EncodeDefault val Constrict: ScalarCommand? = null,
    @EncodeDefault val Spray: ScalarCommand? = null,
    @EncodeDefault val Temperature: ScalarCommand? = null,
    @EncodeDefault val Led: ScalarCommand? = null,
    @EncodeDefault val Position: ScalarCommand? = null,
    @EncodeDefault val HwPositionWithDuration: PositionWithDurationCommand? = null
)

@Serializable
data class ScalarCommand(
    val Value: Double
)

@Serializable
data class PositionWithDurationCommand(
    val Value: Int,
    val Duration: Int
)

// =============================================================================
// INPUT/SENSOR COMMANDS (v4)
// =============================================================================

@Serializable
@SerialName("InputCmd")
data class InputCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val FeatureIndex: Int,
    val Type: String,
    val Command: String
)

@Serializable
@SerialName("InputReading")
data class InputReading(
    override val Id: Int,
    val DeviceIndex: Int,
    val FeatureIndex: Int,
    val Reading: SensorReading
) : ButtplugServerMessage

@Serializable
data class SensorReading(
    @EncodeDefault val Battery: BatteryValue? = null,
    @EncodeDefault val Rssi: RssiValue? = null,
    @EncodeDefault val Button: ButtonValue? = null,
    @EncodeDefault val Pressure: PressureValue? = null
)

@Serializable data class BatteryValue(val Value: Int)
@Serializable data class RssiValue(val Value: Int)
@Serializable data class ButtonValue(val Value: Int)
@Serializable data class PressureValue(val Value: Int)

// =============================================================================
// v3 LEGACY COMMANDS (for backward compatibility)
// =============================================================================

@Serializable
@SerialName("StopDeviceCmd")
data class StopDeviceCmd(
    val Id: Int,
    val DeviceIndex: Int
)

@Serializable
@SerialName("StopAllDevices")
data class StopAllDevices(val Id: Int)

@Serializable
@SerialName("ScalarCmd")
data class ScalarCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Scalars: List<ScalarSubcommand>
)

@Serializable
data class ScalarSubcommand(
    val Index: Int = 0,
    val Scalar: Double,
    val ActuatorType: String = "Vibrate"
)

@Serializable
@SerialName("VibrateCmd")
data class VibrateCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Speeds: List<SpeedSubcommand>
)

@Serializable
data class SpeedSubcommand(
    val Index: Int = 0,
    val Speed: Double
)

@Serializable
@SerialName("RotateCmd")
data class RotateCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Rotations: List<RotationSubcommand>
)

@Serializable
data class RotationSubcommand(
    val Index: Int = 0,
    val Speed: Double,
    val Clockwise: Boolean = true
)

@Serializable
@SerialName("LinearCmd")
data class LinearCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Vectors: List<LinearSubcommand>
)

@Serializable
data class LinearSubcommand(
    val Index: Int = 0,
    val Duration: Int,
    val Position: Double
)

// =============================================================================
// v3 SENSOR COMMANDS (legacy)
// =============================================================================

@Serializable
@SerialName("SensorReadCmd")
data class SensorReadCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val SensorIndex: Int,
    val SensorType: String
)

@Serializable
@SerialName("SensorSubscribeCmd")
data class SensorSubscribeCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val SensorIndex: Int,
    val SensorType: String
)

@Serializable
@SerialName("SensorUnsubscribeCmd")
data class SensorUnsubscribeCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val SensorIndex: Int,
    val SensorType: String
)

@Serializable
@SerialName("SensorReading")
data class SensorReadingV3(
    override val Id: Int,
    val DeviceIndex: Int,
    val SensorIndex: Int,
    val SensorType: String,
    val Data: List<Int>
) : ButtplugServerMessage

// =============================================================================
// v2 SPECIALIZED COMMANDS (legacy)
// =============================================================================

@Serializable
@SerialName("BatteryLevelCmd")
data class BatteryLevelCmd(val Id: Int, val DeviceIndex: Int)

@Serializable
@SerialName("BatteryLevelReading")
data class BatteryLevelReading(
    override val Id: Int,
    val DeviceIndex: Int,
    val BatteryLevel: Double
) : ButtplugServerMessage

@Serializable
@SerialName("RSSILevelCmd")
data class RSSILevelCmd(val Id: Int, val DeviceIndex: Int)

@Serializable
@SerialName("RSSILevelReading")
data class RSSILevelReading(
    override val Id: Int,
    val DeviceIndex: Int,
    val RSSILevel: Double
) : ButtplugServerMessage

@Serializable
@SerialName("RawWriteCmd")
data class RawWriteCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Endpoint: String,
    val Data: List<Int>,
    val WriteWithResponse: Boolean = false
)

@Serializable
@SerialName("RawReadCmd")
data class RawReadCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Endpoint: String,
    val ExpectedLength: Int = 0,
    val WaitForData: Boolean = false
)

@Serializable
@SerialName("RawReading")
data class RawReading(
    override val Id: Int,
    val DeviceIndex: Int,
    val Endpoint: String,
    val Data: List<Int>
) : ButtplugServerMessage

@Serializable
@SerialName("RawSubscribeCmd")
data class RawSubscribeCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Endpoint: String
)

@Serializable
@SerialName("RawUnsubscribeCmd")
data class RawUnsubscribeCmd(
    val Id: Int,
    val DeviceIndex: Int,
    val Endpoint: String
)

// =============================================================================
// ERROR CODES
// =============================================================================

enum class ButtplugErrorCode(val code: Int) {
    ERROR_UNKNOWN(0),
    ERROR_HANDSHAKE(1),
    ERROR_PING(2),
    ERROR_MESSAGE(3),
    ERROR_DEVICE(4);

    companion object {
        fun fromCode(code: Int): ButtplugErrorCode =
            entries.firstOrNull { it.code == code } ?: ERROR_UNKNOWN
    }
}
