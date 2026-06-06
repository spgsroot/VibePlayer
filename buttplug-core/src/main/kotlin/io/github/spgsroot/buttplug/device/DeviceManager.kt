package io.github.spgsroot.buttplug.device

import io.github.spgsroot.buttplug.protocol.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*

/**
 * Manages the device list state, updated from server messages.
 * Handles both v4 (DeviceFeatures map) and v3 (DeviceMessages object) formats.
 */
class DeviceManager {
    private val _devices = MutableStateFlow<List<ButtplugDevice>>(emptyList())
    val devices: StateFlow<List<ButtplugDevice>> = _devices.asStateFlow()

    fun handleDeviceList(deviceList: DeviceList) {
        val parsed = deviceList.Devices.entries.mapNotNull { (key, value) ->
            val index = key.toIntOrNull() ?: return@mapNotNull null
            val obj = value.jsonObject
            parseDeviceInfo(index, obj)
        }.sortedBy { it.index }
        _devices.value = parsed
    }

    private fun parseDeviceInfo(index: Int, obj: JsonObject): ButtplugDevice {
        val name = obj["DeviceName"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val displayName = obj["DeviceDisplayName"]?.jsonPrimitive?.contentOrNull ?: name
        val timingGap = obj["DeviceMessageTimingGap"]?.jsonPrimitive?.longOrNull ?: 0L

        val features = mutableListOf<DeviceFeature>()

        // v4 format: DeviceFeatures (map of index -> feature)
        val deviceFeatures = obj["DeviceFeatures"]?.jsonObject
        if (deviceFeatures != null) {
            deviceFeatures.entries.forEach { (featIdx, featValue) ->
                val featObj = featValue.jsonObject
                val fi = featIdx.toIntOrNull() ?: return@forEach
                features.add(DeviceFeature.fromProtocol(fi, parseV4FeatureObject(featObj)))
            }
        }

        // v3 format: DeviceMessages
        val deviceMessages = obj["DeviceMessages"]?.jsonObject
        if (deviceMessages != null && features.isEmpty()) {
            features.addAll(parseV3DeviceMessages(deviceMessages))
        }

        return ButtplugDevice(
            index = index,
            name = name,
            displayName = displayName,
            messageTimingGap = timingGap,
            features = features
        )
    }

    private fun parseV4FeatureObject(obj: JsonObject): DeviceFeatureV4 {
        return DeviceFeatureV4(
            FeatureDescription = obj["FeatureDescription"]?.jsonPrimitive?.contentOrNull ?: "",
            FeatureIndex = obj["FeatureIndex"]?.jsonPrimitive?.intOrNull ?: 0,
            Output = obj["Output"]?.jsonObject,
            Input = obj["Input"]?.jsonObject
        )
    }

    private fun parseV3DeviceMessages(messages: JsonObject): List<DeviceFeature> {
        val features = mutableListOf<DeviceFeature>()
        var featIndex = 0

        messages.entries.forEach { (msgType, msgValue) ->
            val array = msgValue.jsonArray

            when (msgType) {
                "ScalarCmd" -> {
                    array.forEach { sub ->
                        val subObj = sub.jsonObject
                        val desc = subObj["FeatureDescriptor"]?.jsonPrimitive?.contentOrNull ?: ""
                        val stepCount = subObj["StepCount"]?.jsonPrimitive?.intOrNull ?: 20
                        val actuatorName = subObj["ActuatorType"]?.jsonPrimitive?.contentOrNull ?: "Vibrate"
                        val actuator = ActuatorType.fromName(actuatorName) ?: return@forEach

                        features.add(DeviceFeature(
                            index = featIndex++,
                            description = desc,
                            outputTypes = listOf(actuator),
                            stepCount = mapOf(actuator to stepCount)
                        ))
                    }
                }
                "VibrateCmd" -> {
                    array.forEach { sub ->
                        val subObj = sub.jsonObject
                        val fi = subObj["Index"]?.jsonPrimitive?.intOrNull ?: featIndex
                        features.add(DeviceFeature(
                            index = fi,
                            outputTypes = listOf(ActuatorType.Vibrate),
                            stepCount = mapOf(ActuatorType.Vibrate to 20)
                        ))
                        featIndex = maxOf(featIndex, fi + 1)
                    }
                }
                "RotateCmd" -> {
                    array.forEach { sub ->
                        val subObj = sub.jsonObject
                        val fi = subObj["Index"]?.jsonPrimitive?.intOrNull ?: featIndex
                        features.add(DeviceFeature(
                            index = fi,
                            outputTypes = listOf(ActuatorType.Rotate),
                            stepCount = mapOf(ActuatorType.Rotate to 20)
                        ))
                        featIndex = maxOf(featIndex, fi + 1)
                    }
                }
                "LinearCmd" -> {
                    array.forEach { sub ->
                        val subObj = sub.jsonObject
                        val fi = subObj["Index"]?.jsonPrimitive?.intOrNull ?: featIndex
                        features.add(DeviceFeature(
                            index = fi,
                            outputTypes = listOf(ActuatorType.Position),
                            stepCount = mapOf(ActuatorType.Position to 100)
                        ))
                        featIndex = maxOf(featIndex, fi + 1)
                    }
                }
                "SensorReadCmd", "SensorSubscribeCmd" -> {
                    array.forEach { sub ->
                        val subObj = sub.jsonObject
                        val sensorName = subObj["SensorType"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val sensorType = SensorType.fromName(sensorName) ?: return@forEach
                        val si = subObj["SensorIndex"]?.jsonPrimitive?.intOrNull ?: featIndex
                        features.add(DeviceFeature(
                            index = si,
                            inputTypes = listOf(sensorType),
                            inputCommands = mapOf(sensorType to listOf(SensorCommand.Read, SensorCommand.Subscribe, SensorCommand.Unsubscribe))
                        ))
                        featIndex = maxOf(featIndex, si + 1)
                    }
                }
                // StopDeviceCmd, RawReadCmd, RawWriteCmd — no feature descriptors to extract
            }
        }
        return features
    }

    fun handleDeviceAdded(device: DeviceAddedV3) {
        _devices.update { current ->
            val newDevice = ButtplugDevice(
                index = device.DeviceIndex,
                name = device.DeviceName,
                displayName = device.DeviceDisplayName
            )
            (current + newDevice).distinctBy { it.index }.sortedBy { it.index }
        }
    }

    fun handleDeviceRemoved(removed: DeviceRemoved) {
        _devices.update { current ->
            current.filter { it.index != removed.DeviceIndex }
        }
    }

    fun clear() {
        _devices.value = emptyList()
    }

    fun getDevice(index: Int): ButtplugDevice? =
        _devices.value.firstOrNull { it.index == index }

    fun isEmpty(): Boolean = _devices.value.isEmpty()
}
