package io.github.spgsroot.buttplug.device

import io.github.spgsroot.buttplug.protocol.DeviceFeatureV4
import io.github.spgsroot.buttplug.protocol.DeviceInfoV4
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * High-level device model exposed to library consumers.
 */
data class ButtplugDevice(
    val index: Int,
    val name: String,
    val displayName: String = name,
    val messageTimingGap: Long = 0,
    val features: List<DeviceFeature> = emptyList()
) {
    companion object {
        fun fromProtocol(info: DeviceInfoV4): ButtplugDevice = ButtplugDevice(
            index = info.DeviceIndex,
            name = info.DeviceName,
            displayName = info.DeviceDisplayName,
            messageTimingGap = info.DeviceMessageTimingGap,
            features = info.DeviceFeatures.map { (key, value) ->
                DeviceFeature.fromProtocol(key.toInt(), value)
            }
        )
    }

    fun hasActuator(actuatorType: ActuatorType): Boolean =
        features.any { it.outputTypes.contains(actuatorType) }

    fun hasSensor(sensorType: SensorType): Boolean =
        features.any { it.inputTypes.contains(sensorType) }

    fun vibrateFeatures(): List<DeviceFeature> =
        features.filter { it.outputTypes.contains(ActuatorType.Vibrate) }

    fun rotateFeatures(): List<DeviceFeature> =
        features.filter { it.outputTypes.contains(ActuatorType.Rotate) }
}

/**
 * A single feature (actuator or sensor) on a device.
 */
data class DeviceFeature(
    val index: Int,
    val description: String = "",
    val outputTypes: List<ActuatorType> = emptyList(),
    val inputTypes: List<SensorType> = emptyList(),
    val stepCount: Map<ActuatorType, Int> = emptyMap(), // max step for each actuator
    val inputCommands: Map<SensorType, List<SensorCommand>> = emptyMap()
) {
    companion object {
        fun fromProtocol(index: Int, feature: DeviceFeatureV4): DeviceFeature {
            val outputs = mutableListOf<ActuatorType>()
            val stepCounts = mutableMapOf<ActuatorType, Int>()
            val inputs = mutableListOf<SensorType>()
            val inputCmds = mutableMapOf<SensorType, List<SensorCommand>>()

            feature.Output?.let { out ->
                out.keys.forEach { typeName ->
                    ActuatorType.fromName(typeName)?.let { actuator ->
                        outputs.add(actuator)
                        val valueJson = (out[typeName] as? JsonObject)?.get("Value")
                        if (valueJson is JsonArray && valueJson.size >= 2) {
                            val maxStep = (valueJson[1] as? JsonPrimitive)?.content?.toIntOrNull() ?: 20
                            stepCounts[actuator] = maxStep
                        }
                    }
                }
            }

            feature.Input?.let { inp ->
                inp.keys.forEach { typeName ->
                    SensorType.fromName(typeName)?.let { sensor ->
                        inputs.add(sensor)
                        val commands = (inp[typeName] as? JsonObject)?.get("Command")
                        val cmds = when (commands) {
                            is JsonArray ->
                                commands.mapNotNull { (it as? JsonPrimitive)?.content?.let(SensorCommand::fromName) }
                            is JsonPrimitive ->
                                listOfNotNull(SensorCommand.fromName(commands.content))
                            else -> emptyList()
                        }
                        inputCmds[sensor] = cmds
                    }
                }
            }

            return DeviceFeature(
                index = index,
                description = feature.FeatureDescription,
                outputTypes = outputs,
                inputTypes = inputs,
                stepCount = stepCounts,
                inputCommands = inputCmds
            )
        }
    }
}

/**
 * Supported actuator types.
 */
enum class ActuatorType {
    Vibrate, Rotate, Oscillate, Constrict, Spray, Temperature, Led,
    Position, HwPositionWithDuration;

    companion object {
        fun fromName(name: String): ActuatorType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Supported sensor types.
 */
enum class SensorType {
    Battery, Rssi, Button, Pressure, Depth, Position;

    companion object {
        fun fromName(name: String): SensorType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Sensor command types.
 */
enum class SensorCommand {
    Read, Subscribe, Unsubscribe;

    companion object {
        fun fromName(name: String): SensorCommand? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
