package io.github.spgsroot.buttplug.device

import io.github.spgsroot.buttplug.protocol.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun testDevicesJson(vararg devices: Pair<Int, String>): JsonObject = buildJsonObject {
    devices.forEach { (index, name) ->
        put(index.toString(), buildJsonObject {
            put("DeviceName", JsonPrimitive(name))
            put("DeviceIndex", JsonPrimitive(index))
        })
    }
}

// =============================================================================
// DeviceManager Tests
// =============================================================================

class DeviceManagerTests {

    @Test
    fun `device list is initially empty`() {
        val manager = DeviceManager()
        assertTrue(manager.isEmpty())
        assertEquals(0, manager.devices.value.size)
    }

    @Test
    fun `handleDeviceList updates devices list correctly`() {
        val manager = DeviceManager()
        val deviceList = DeviceList(
            Id = 1,
            Devices = testDevicesJson(0 to "TestDevice1", 1 to "TestDevice2")
        )
        manager.handleDeviceList(deviceList)

        assertFalse(manager.isEmpty())
        assertEquals(2, manager.devices.value.size)
        assertEquals("TestDevice1", manager.devices.value[0].name)
        assertEquals("TestDevice2", manager.devices.value[1].name)
    }

    @Test
    fun `handleDeviceList replaces existing devices list`() {
        val manager = DeviceManager()
        val first = DeviceList(
            Id = 1,
            Devices = testDevicesJson(0 to "DeviceA")
        )
        val second = DeviceList(
            Id = 2,
            Devices = testDevicesJson(5 to "DeviceB", 3 to "DeviceC")
        )

        manager.handleDeviceList(first)
        assertEquals(1, manager.devices.value.size)
        assertEquals("DeviceA", manager.devices.value[0].name)

        manager.handleDeviceList(second)
        assertEquals(2, manager.devices.value.size)
        assertEquals("DeviceC", manager.devices.value[0].name)
        assertEquals("DeviceB", manager.devices.value[1].name)
    }

    @Test
    fun `devices are sorted by index`() {
        val manager = DeviceManager()
        val deviceList = DeviceList(
            Id = 1,
            Devices = testDevicesJson(
                3 to "Third",
                0 to "First",
                2 to "Second",
                5 to "Fifth",
                4 to "Fourth"
            )
        )
        manager.handleDeviceList(deviceList)

        val indices = manager.devices.value.map { it.index }
        assertEquals(listOf(0, 2, 3, 4, 5), indices)
    }

    @Test
    fun `handleDeviceAdded adds new device`() {
        val manager = DeviceManager()
        // Pre-populate with one device
        manager.handleDeviceList(
            DeviceList(
                Id = 1,
                Devices = testDevicesJson(0 to "Existing")
            )
        )

        val added = DeviceAddedV3(
            DeviceName = "NewDevice",
            DeviceIndex = 5,
            DeviceDisplayName = "New Device Display"
        )
        manager.handleDeviceAdded(added)

        assertEquals(2, manager.devices.value.size)
        assertEquals(0, manager.devices.value[0].index)
        assertEquals(5, manager.devices.value[1].index)
        assertEquals("NewDevice", manager.devices.value[1].name)
        assertEquals("New Device Display", manager.devices.value[1].displayName)
    }

    @Test
    fun `handleDeviceAdded does not duplicate existing device`() {
        val manager = DeviceManager()
        manager.handleDeviceList(
            DeviceList(
                Id = 1,
                Devices = testDevicesJson(3 to "Existing")
            )
        )

        val duplicate = DeviceAddedV3(DeviceName = "Duplicate", DeviceIndex = 3)
        manager.handleDeviceAdded(duplicate)

        assertEquals(1, manager.devices.value.size)
        assertEquals("Existing", manager.devices.value[0].name)
    }

    @Test
    fun `handleDeviceAdded adds multiple devices and prevents duplicates`() {
        val manager = DeviceManager()

        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "Device1", DeviceIndex = 1))
        assertEquals(1, manager.devices.value.size)

        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "Device2", DeviceIndex = 2))
        assertEquals(2, manager.devices.value.size)

        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "Device3", DeviceIndex = 0))
        assertEquals(3, manager.devices.value.size)
        // Should be sorted: 0, 1, 2
        assertEquals(listOf(0, 1, 2), manager.devices.value.map { it.index })

        // Adding duplicate should not increase count
        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "Duplicate", DeviceIndex = 1))
        assertEquals(3, manager.devices.value.size)
    }

    @Test
    fun `handleDeviceRemoved removes correct device`() {
        val manager = DeviceManager()
        manager.handleDeviceList(
            DeviceList(
                Id = 1,
                Devices = testDevicesJson(0 to "DeviceA", 1 to "DeviceB", 2 to "DeviceC")
            )
        )

        manager.handleDeviceRemoved(DeviceRemoved(DeviceIndex = 1))

        assertEquals(2, manager.devices.value.size)
        assertEquals(listOf(0, 2), manager.devices.value.map { it.index })
        assertEquals("DeviceA", manager.devices.value[0].name)
        assertEquals("DeviceC", manager.devices.value[1].name)
    }

    @Test
    fun `handleDeviceRemoved does nothing for non-existent index`() {
        val manager = DeviceManager()
        manager.handleDeviceList(
            DeviceList(
                Id = 1,
                Devices = testDevicesJson(0 to "Only")
            )
        )

        manager.handleDeviceRemoved(DeviceRemoved(DeviceIndex = 99))

        assertEquals(1, manager.devices.value.size)
        assertEquals("Only", manager.devices.value[0].name)
    }

    @Test
    fun `clear empties the device list`() {
        val manager = DeviceManager()
        manager.handleDeviceList(
            DeviceList(
                Id = 1,
                Devices = testDevicesJson(0 to "Device1", 1 to "Device2")
            )
        )
        assertFalse(manager.isEmpty())

        manager.clear()

        assertTrue(manager.isEmpty())
        assertEquals(0, manager.devices.value.size)
    }

    @Test
    fun `getDevice finds device by index`() {
        val manager = DeviceManager()
        manager.handleDeviceList(
            DeviceList(
                Id = 1,
                Devices = testDevicesJson(0 to "Zero", 1 to "One", 5 to "Five")
            )
        )

        val device0 = manager.getDevice(0)
        assertNotNull(device0)
        assertEquals("Zero", device0.name)

        val device5 = manager.getDevice(5)
        assertNotNull(device5)
        assertEquals("Five", device5.name)

        val notFound = manager.getDevice(99)
        assertNull(notFound)
    }

    @Test
    fun `getDevice returns null for empty manager`() {
        val manager = DeviceManager()
        assertNull(manager.getDevice(0))
        assertNull(manager.getDevice(42))
    }

    @Test
    fun `isEmpty returns true for empty and false for non-empty`() {
        val manager = DeviceManager()
        assertTrue(manager.isEmpty())

        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "Test", DeviceIndex = 0))
        assertFalse(manager.isEmpty())

        manager.clear()
        assertTrue(manager.isEmpty())
    }

    @Test
    fun `multiple adds and removes in sequence maintain correct state`() {
        val manager = DeviceManager()

        // Add 3 devices
        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "A", DeviceIndex = 0))
        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "B", DeviceIndex = 1))
        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "C", DeviceIndex = 2))
        assertEquals(3, manager.devices.value.size)
        assertEquals(listOf(0, 1, 2), manager.devices.value.map { it.index })

        // Remove middle device
        manager.handleDeviceRemoved(DeviceRemoved(DeviceIndex = 1))
        assertEquals(2, manager.devices.value.size)
        assertEquals(listOf(0, 2), manager.devices.value.map { it.index })

        // Add a new device with a higher index
        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "D", DeviceIndex = 10))
        assertEquals(3, manager.devices.value.size)
        assertEquals(listOf(0, 2, 10), manager.devices.value.map { it.index })

        // Remove first device
        manager.handleDeviceRemoved(DeviceRemoved(DeviceIndex = 0))
        assertEquals(2, manager.devices.value.size)
        assertEquals(listOf(2, 10), manager.devices.value.map { it.index })

        // Remove last device
        manager.handleDeviceRemoved(DeviceRemoved(DeviceIndex = 10))
        assertEquals(1, manager.devices.value.size)
        assertEquals("C", manager.devices.value[0].name)

        // Remove last remaining
        manager.handleDeviceRemoved(DeviceRemoved(DeviceIndex = 2))
        assertTrue(manager.isEmpty())

        // Re-add after all removed
        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "Fresh", DeviceIndex = 7))
        assertEquals(1, manager.devices.value.size)
        assertEquals("Fresh", manager.devices.value[0].name)
    }

    @Test
    fun `handleDeviceList with empty Devices list clears everything`() {
        val manager = DeviceManager()
        manager.handleDeviceAdded(DeviceAddedV3(DeviceName = "Test", DeviceIndex = 0))
        assertFalse(manager.isEmpty())

        manager.handleDeviceList(DeviceList(Id = 1, Devices = buildJsonObject { }))
        assertTrue(manager.isEmpty())
    }
}

// =============================================================================
// ActuatorType / SensorType / SensorCommand Enum Tests
// =============================================================================

class ActuatorTypeTests {

    @Test
    fun `fromName returns correct ActuatorType for exact name`() {
        assertEquals(ActuatorType.Vibrate, ActuatorType.fromName("Vibrate"))
        assertEquals(ActuatorType.Rotate, ActuatorType.fromName("Rotate"))
        assertEquals(ActuatorType.Oscillate, ActuatorType.fromName("Oscillate"))
        assertEquals(ActuatorType.Constrict, ActuatorType.fromName("Constrict"))
        assertEquals(ActuatorType.Spray, ActuatorType.fromName("Spray"))
        assertEquals(ActuatorType.Temperature, ActuatorType.fromName("Temperature"))
        assertEquals(ActuatorType.Led, ActuatorType.fromName("Led"))
        assertEquals(ActuatorType.Position, ActuatorType.fromName("Position"))
        assertEquals(ActuatorType.HwPositionWithDuration, ActuatorType.fromName("HwPositionWithDuration"))
    }

    @Test
    fun `fromName is case insensitive`() {
        assertEquals(ActuatorType.Vibrate, ActuatorType.fromName("vibrate"))
        assertEquals(ActuatorType.Rotate, ActuatorType.fromName("ROTATE"))
        assertEquals(ActuatorType.Oscillate, ActuatorType.fromName("OsCiLlAtE"))
        assertEquals(ActuatorType.Constrict, ActuatorType.fromName("CONSTRICT"))
        assertEquals(ActuatorType.Led, ActuatorType.fromName("led"))
    }

    @Test
    fun `fromName returns null for unknown type`() {
        assertNull(ActuatorType.fromName("NonExistent"))
        assertNull(ActuatorType.fromName(""))
        assertNull(ActuatorType.fromName("vibrate_typo"))
    }
}

class SensorTypeTests {

    @Test
    fun `fromName returns correct SensorType for exact name`() {
        assertEquals(SensorType.Battery, SensorType.fromName("Battery"))
        assertEquals(SensorType.Rssi, SensorType.fromName("Rssi"))
        assertEquals(SensorType.Button, SensorType.fromName("Button"))
        assertEquals(SensorType.Pressure, SensorType.fromName("Pressure"))
        assertEquals(SensorType.Depth, SensorType.fromName("Depth"))
        assertEquals(SensorType.Position, SensorType.fromName("Position"))
    }

    @Test
    fun `fromName is case insensitive`() {
        assertEquals(SensorType.Battery, SensorType.fromName("battery"))
        assertEquals(SensorType.Rssi, SensorType.fromName("RSSI"))
        assertEquals(SensorType.Button, SensorType.fromName("BuTtOn"))
        assertEquals(SensorType.Pressure, SensorType.fromName("PRESSURE"))
        assertEquals(SensorType.Depth, SensorType.fromName("depth"))
    }

    @Test
    fun `fromName returns null for unknown sensor type`() {
        assertNull(SensorType.fromName("UnknownSensor"))
        assertNull(SensorType.fromName(""))
        assertNull(SensorType.fromName("batry")) // typo
    }
}

class SensorCommandTests {

    @Test
    fun `fromName returns correct SensorCommand for exact name`() {
        assertEquals(SensorCommand.Read, SensorCommand.fromName("Read"))
        assertEquals(SensorCommand.Subscribe, SensorCommand.fromName("Subscribe"))
        assertEquals(SensorCommand.Unsubscribe, SensorCommand.fromName("Unsubscribe"))
    }

    @Test
    fun `fromName is case insensitive`() {
        assertEquals(SensorCommand.Read, SensorCommand.fromName("read"))
        assertEquals(SensorCommand.Subscribe, SensorCommand.fromName("SUBSCRIBE"))
        assertEquals(SensorCommand.Unsubscribe, SensorCommand.fromName("UnSuBsCrIbE"))
    }

    @Test
    fun `fromName returns null for unknown command`() {
        assertNull(SensorCommand.fromName("Write"))
        assertNull(SensorCommand.fromName(""))
        assertNull(SensorCommand.fromName("Rea")) // partial
    }
}

// =============================================================================
// DeviceFeature fromProtocol Parsing Tests
// =============================================================================

class DeviceFeatureFromProtocolTests {

    // --- Output parsing tests ---

    @Test
    fun `parse feature with Vibrate output and step count`() {
        // Build JSON: {"Vibrate": {"Value": [0, 50]}}
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(50))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Main vibrator",
            FeatureIndex = 0,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(0, feature.index)
        assertEquals("Main vibrator", feature.description)
        assertEquals(listOf(ActuatorType.Vibrate), feature.outputTypes)
        assertTrue(feature.inputTypes.isEmpty())
        assertEquals(50, feature.stepCount[ActuatorType.Vibrate])
        assertTrue(feature.inputCommands.isEmpty())
    }

    @Test
    fun `parse feature with multiple output types`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(20))
                }
            }
            putJsonObject("Rotate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(100))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Dual motor",
            FeatureIndex = 1,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(1, protocolFeature)

        assertEquals(listOf(ActuatorType.Vibrate, ActuatorType.Rotate), feature.outputTypes)
        assertEquals(20, feature.stepCount[ActuatorType.Vibrate])
        assertEquals(100, feature.stepCount[ActuatorType.Rotate])
    }

    @Test
    fun `parse feature with unknown actuator type is skipped`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(20))
                }
            }
            putJsonObject("UnknownActuator") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(100))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Mixed",
            FeatureIndex = 0,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(listOf(ActuatorType.Vibrate), feature.outputTypes)
        assertEquals(1, feature.stepCount.size)
    }

    @Test
    fun `parse feature step count omitted when Value array too small`() {
        // Value array with only one element — stepCount entry is never added
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Single value",
            FeatureIndex = 0,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)
        assertTrue(feature.stepCount.isEmpty())
    }

    @Test
    fun `parse feature step count omitted when Value key is missing`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                put("SomeOtherKey", JsonPrimitive("ignored"))
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "No Value key",
            FeatureIndex = 0,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)
        assertTrue(feature.stepCount.isEmpty())
    }

    @Test
    fun `parse feature with step count of 0`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(0))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Zero-step vibrator",
            FeatureIndex = 3,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(3, protocolFeature)
        assertEquals(0, feature.stepCount[ActuatorType.Vibrate])
    }

    @Test
    fun `parse feature with non-integer step count defaults to 20`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive("not_a_number"))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Bad step count",
            FeatureIndex = 0,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)
        assertEquals(20, feature.stepCount[ActuatorType.Vibrate])
    }

    // --- Input parsing tests ---

    @Test
    fun `parse feature with Battery input and Read command`() {
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                put("Command", JsonPrimitive("Read"))
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Battery sensor",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertTrue(feature.outputTypes.isEmpty())
        assertEquals(listOf(SensorType.Battery), feature.inputTypes)
        assertEquals(listOf(SensorCommand.Read), feature.inputCommands[SensorType.Battery])
    }

    @Test
    fun `parse feature with Button input and Subscribe command as array`() {
        val inputJson = buildJsonObject {
            putJsonObject("Button") {
                putJsonArray("Command") {
                    add(JsonPrimitive("Subscribe"))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Button sensor",
            FeatureIndex = 1,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(1, protocolFeature)

        assertEquals(listOf(SensorType.Button), feature.inputTypes)
        assertEquals(listOf(SensorCommand.Subscribe), feature.inputCommands[SensorType.Button])
    }

    @Test
    fun `parse feature with multiple commands in array`() {
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                putJsonArray("Command") {
                    add(JsonPrimitive("Read"))
                    add(JsonPrimitive("Subscribe"))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Multi-command battery",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(
            listOf(SensorCommand.Read, SensorCommand.Subscribe),
            feature.inputCommands[SensorType.Battery]
        )
    }

    @Test
    fun `parse feature with multiple sensor types`() {
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                put("Command", JsonPrimitive("Read"))
            }
            putJsonObject("Rssi") {
                put("Command", JsonPrimitive("Subscribe"))
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Multi-sensor",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(listOf(SensorType.Battery, SensorType.Rssi), feature.inputTypes)
        assertEquals(listOf(SensorCommand.Read), feature.inputCommands[SensorType.Battery])
        assertEquals(listOf(SensorCommand.Subscribe), feature.inputCommands[SensorType.Rssi])
    }

    @Test
    fun `parse feature with unknown sensor type is skipped in input`() {
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                put("Command", JsonPrimitive("Read"))
            }
            putJsonObject("UnknownSensor") {
                put("Command", JsonPrimitive("Read"))
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Mixed sensors",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(listOf(SensorType.Battery), feature.inputTypes)
        assertEquals(1, feature.inputCommands.size)
    }

    @Test
    fun `parse feature with unknown sensor command is skipped`() {
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                putJsonArray("Command") {
                    add(JsonPrimitive("Read"))
                    add(JsonPrimitive("UnknownCommand"))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Mixed commands",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        // Only Read should survive; UnknownCommand is filtered out by mapNotNull
        assertEquals(listOf(SensorCommand.Read), feature.inputCommands[SensorType.Battery])
    }

    // --- Edge cases ---

    @Test
    fun `parse feature with no Output or Input`() {
        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Empty feature",
            FeatureIndex = 7,
            Output = null,
            Input = null
        )

        val feature = DeviceFeature.fromProtocol(7, protocolFeature)

        assertEquals(7, feature.index)
        assertEquals("Empty feature", feature.description)
        assertTrue(feature.outputTypes.isEmpty())
        assertTrue(feature.inputTypes.isEmpty())
        assertTrue(feature.stepCount.isEmpty())
        assertTrue(feature.inputCommands.isEmpty())
    }

    @Test
    fun `parse feature with empty Output object`() {
        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Empty output",
            FeatureIndex = 0,
            Output = buildJsonObject { }
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertTrue(feature.outputTypes.isEmpty())
        assertTrue(feature.stepCount.isEmpty())
    }

    @Test
    fun `parse feature with empty Input object`() {
        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Empty input",
            FeatureIndex = 0,
            Input = buildJsonObject { }
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertTrue(feature.inputTypes.isEmpty())
        assertTrue(feature.inputCommands.isEmpty())
    }

    @Test
    fun `parse feature with output and input simultaneously`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(30))
                }
            }
        }
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                put("Command", JsonPrimitive("Read"))
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Hybrid feature",
            FeatureIndex = 2,
            Output = outputJson,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(2, protocolFeature)

        assertEquals(listOf(ActuatorType.Vibrate), feature.outputTypes)
        assertEquals(30, feature.stepCount[ActuatorType.Vibrate])
        assertEquals(listOf(SensorType.Battery), feature.inputTypes)
        assertEquals(listOf(SensorCommand.Read), feature.inputCommands[SensorType.Battery])
    }

    @Test
    fun `parse feature with Command as non-JSON primitive and non-array defaults to empty`() {
        // When Command is an unexpected JSON type (e.g., JsonObject), the when branch falls to else -> emptyList()
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                putJsonObject("Command") {
                    put("nested", JsonPrimitive("value"))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Weird command shape",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(listOf(SensorType.Battery), feature.inputTypes)
        // Command was a JsonObject, so when falls to else -> emptyList()
        assertTrue(feature.inputCommands[SensorType.Battery]?.isEmpty() ?: true)
    }

    @Test
    fun `parse feature preserves step count per actuator independently`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(10))
                }
            }
            putJsonObject("Rotate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(50))
                }
            }
            putJsonObject("Led") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(255))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Triple actuator",
            FeatureIndex = 0,
            Output = outputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(3, feature.stepCount.size)
        assertEquals(10, feature.stepCount[ActuatorType.Vibrate])
        assertEquals(50, feature.stepCount[ActuatorType.Rotate])
        assertEquals(255, feature.stepCount[ActuatorType.Led])
    }

    @Test
    fun `parse feature input commands are case insensitive`() {
        val inputJson = buildJsonObject {
            putJsonObject("Button") {
                putJsonArray("Command") {
                    add(JsonPrimitive("subscribe"))
                    add(JsonPrimitive("UNSUBSCRIBE"))
                }
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Case-insensitive commands",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(
            listOf(SensorCommand.Subscribe, SensorCommand.Unsubscribe),
            feature.inputCommands[SensorType.Button]
        )
    }

    @Test
    fun `parse feature with Subscribe command as plain string (not array)`() {
        // When Command is a single JsonPrimitive string (not in array)
        val inputJson = buildJsonObject {
            putJsonObject("Button") {
                put("Command", JsonPrimitive("Subscribe"))
            }
        }

        val protocolFeature = DeviceFeatureV4(
            FeatureDescription = "Single string command",
            FeatureIndex = 0,
            Input = inputJson
        )

        val feature = DeviceFeature.fromProtocol(0, protocolFeature)

        assertEquals(listOf(SensorType.Button), feature.inputTypes)
        assertEquals(listOf(SensorCommand.Subscribe), feature.inputCommands[SensorType.Button])
    }
}

// =============================================================================
// ButtplugDevice Tests
// =============================================================================

class ButtplugDeviceTests {

    @Test
    fun `fromProtocol correctly maps all fields`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(20))
                }
            }
        }
        val features = mapOf(
            "0" to DeviceFeatureV4(
                FeatureDescription = "Vibe motor",
                FeatureIndex = 0,
                Output = outputJson
            )
        )
        val deviceInfo = DeviceInfoV4(
            DeviceName = "Lovense",
            DeviceIndex = 3,
            DeviceDisplayName = "Lovense Edge 2",
            DeviceMessageTimingGap = 150L,
            DeviceFeatures = features
        )

        val device = ButtplugDevice.fromProtocol(deviceInfo)

        assertEquals(3, device.index)
        assertEquals("Lovense", device.name)
        assertEquals("Lovense Edge 2", device.displayName)
        assertEquals(150L, device.messageTimingGap)
        assertEquals(1, device.features.size)
        assertEquals(0, device.features[0].index)
        assertEquals("Vibe motor", device.features[0].description)
    }

    @Test
    fun `fromProtocol defaults displayName to name when displayName equals name`() {
        val deviceInfo = DeviceInfoV4(
            DeviceName = "TestDevice",
            DeviceIndex = 0,
            // DeviceDisplayName defaults to DeviceName in the protocol class default
            DeviceFeatures = emptyMap()
        )

        val device = ButtplugDevice.fromProtocol(deviceInfo)

        assertEquals("TestDevice", device.name)
        assertEquals("TestDevice", device.displayName)
    }

    @Test
    fun `fromProtocol with empty features produces empty feature list`() {
        val deviceInfo = DeviceInfoV4(
            DeviceName = "NoFeatures",
            DeviceIndex = 0
        )

        val device = ButtplugDevice.fromProtocol(deviceInfo)

        assertEquals(0, device.features.size)
    }

    @Test
    fun `hasActuator returns true for present actuator`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(20))
                }
            }
        }
        val device = ButtplugDevice(
            index = 0,
            name = "TestDevice",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    outputTypes = listOf(ActuatorType.Vibrate),
                    stepCount = mapOf(ActuatorType.Vibrate to 20)
                )
            )
        )

        assertTrue(device.hasActuator(ActuatorType.Vibrate))
    }

    @Test
    fun `hasActuator returns false for absent actuator`() {
        val device = ButtplugDevice(
            index = 0,
            name = "TestDevice",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    outputTypes = listOf(ActuatorType.Vibrate),
                    stepCount = mapOf(ActuatorType.Vibrate to 20)
                )
            )
        )

        assertFalse(device.hasActuator(ActuatorType.Rotate))
        assertFalse(device.hasActuator(ActuatorType.Led))
    }

    @Test
    fun `hasActuator scans across multiple features`() {
        val device = ButtplugDevice(
            index = 0,
            name = "MultiFeature",
            features = listOf(
                DeviceFeature(index = 0, outputTypes = listOf(ActuatorType.Vibrate)),
                DeviceFeature(index = 1, outputTypes = listOf(ActuatorType.Rotate)),
                DeviceFeature(index = 2, outputTypes = listOf(ActuatorType.Led))
            )
        )

        assertTrue(device.hasActuator(ActuatorType.Vibrate))
        assertTrue(device.hasActuator(ActuatorType.Rotate))
        assertTrue(device.hasActuator(ActuatorType.Led))
        assertFalse(device.hasActuator(ActuatorType.Oscillate))
    }

    @Test
    fun `hasSensor returns true for present sensor`() {
        val device = ButtplugDevice(
            index = 0,
            name = "SensorDevice",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    inputTypes = listOf(SensorType.Battery),
                    inputCommands = mapOf(SensorType.Battery to listOf(SensorCommand.Read))
                )
            )
        )

        assertTrue(device.hasSensor(SensorType.Battery))
    }

    @Test
    fun `hasSensor returns false for absent sensor`() {
        val device = ButtplugDevice(
            index = 0,
            name = "SensorDevice",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    inputTypes = listOf(SensorType.Battery),
                    inputCommands = mapOf(SensorType.Battery to listOf(SensorCommand.Read))
                )
            )
        )

        assertFalse(device.hasSensor(SensorType.Button))
        assertFalse(device.hasSensor(SensorType.Pressure))
    }

    @Test
    fun `hasSensor scans across multiple features`() {
        val device = ButtplugDevice(
            index = 0,
            name = "MultiSensor",
            features = listOf(
                DeviceFeature(index = 0, inputTypes = listOf(SensorType.Battery)),
                DeviceFeature(index = 1, inputTypes = listOf(SensorType.Button)),
                DeviceFeature(index = 2, inputTypes = listOf(SensorType.Pressure))
            )
        )

        assertTrue(device.hasSensor(SensorType.Battery))
        assertTrue(device.hasSensor(SensorType.Button))
        assertTrue(device.hasSensor(SensorType.Pressure))
        assertFalse(device.hasSensor(SensorType.Depth))
    }

    @Test
    fun `vibrateFeatures filters correctly`() {
        val vibrateFeature = DeviceFeature(
            index = 0,
            outputTypes = listOf(ActuatorType.Vibrate),
            stepCount = mapOf(ActuatorType.Vibrate to 20)
        )
        val rotateFeature = DeviceFeature(
            index = 1,
            outputTypes = listOf(ActuatorType.Rotate),
            stepCount = mapOf(ActuatorType.Rotate to 100)
        )
        val dualFeature = DeviceFeature(
            index = 2,
            outputTypes = listOf(ActuatorType.Vibrate, ActuatorType.Rotate),
            stepCount = mapOf(ActuatorType.Vibrate to 10, ActuatorType.Rotate to 50)
        )
        val sensorOnlyFeature = DeviceFeature(
            index = 3,
            inputTypes = listOf(SensorType.Battery)
        )

        val device = ButtplugDevice(
            index = 0,
            name = "MixedDevice",
            features = listOf(vibrateFeature, rotateFeature, dualFeature, sensorOnlyFeature)
        )

        val vibrateFeatures = device.vibrateFeatures()
        assertEquals(2, vibrateFeatures.size)
        assertEquals(listOf(0, 2), vibrateFeatures.map { it.index })

        // Verify the dual feature is included (has Vibrate in outputTypes)
        assertTrue(vibrateFeatures.any { it.index == 2 && it.outputTypes.containsAll(listOf(ActuatorType.Vibrate, ActuatorType.Rotate)) })
    }

    @Test
    fun `rotateFeatures filters correctly`() {
        val vibrateFeature = DeviceFeature(
            index = 0,
            outputTypes = listOf(ActuatorType.Vibrate),
            stepCount = mapOf(ActuatorType.Vibrate to 20)
        )
        val rotateFeature = DeviceFeature(
            index = 1,
            outputTypes = listOf(ActuatorType.Rotate),
            stepCount = mapOf(ActuatorType.Rotate to 100)
        )
        val dualFeature = DeviceFeature(
            index = 2,
            outputTypes = listOf(ActuatorType.Vibrate, ActuatorType.Rotate),
            stepCount = mapOf(ActuatorType.Vibrate to 10, ActuatorType.Rotate to 50)
        )

        val device = ButtplugDevice(
            index = 0,
            name = "RotatingDevice",
            features = listOf(vibrateFeature, rotateFeature, dualFeature)
        )

        val rotateFeatures = device.rotateFeatures()
        assertEquals(2, rotateFeatures.size)
        assertEquals(listOf(1, 2), rotateFeatures.map { it.index })
    }

    @Test
    fun `vibrateFeatures returns empty list when no vibrate features`() {
        val device = ButtplugDevice(
            index = 0,
            name = "NoVibe",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    outputTypes = listOf(ActuatorType.Rotate),
                    stepCount = mapOf(ActuatorType.Rotate to 100)
                )
            )
        )

        assertTrue(device.vibrateFeatures().isEmpty())
    }

    @Test
    fun `rotateFeatures returns empty list when no rotate features`() {
        val device = ButtplugDevice(
            index = 0,
            name = "NoRotate",
            features = listOf(
                DeviceFeature(
                    index = 0,
                    outputTypes = listOf(ActuatorType.Vibrate),
                    stepCount = mapOf(ActuatorType.Vibrate to 20)
                )
            )
        )

        assertTrue(device.rotateFeatures().isEmpty())
    }

    @Test
    fun `vibrateFeatures and rotateFeatures return empty for device with no features`() {
        val device = ButtplugDevice(
            index = 0,
            name = "Bare",
            features = emptyList()
        )

        assertTrue(device.vibrateFeatures().isEmpty())
        assertTrue(device.rotateFeatures().isEmpty())
    }

    @Test
    fun `fromProtocol correctly maps multiple features with mixed types`() {
        val outputJson = buildJsonObject {
            putJsonObject("Vibrate") {
                putJsonArray("Value") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(20))
                }
            }
        }
        val inputJson = buildJsonObject {
            putJsonObject("Battery") {
                put("Command", JsonPrimitive("Read"))
            }
        }
        val features = mapOf(
            "0" to DeviceFeatureV4(
                FeatureDescription = "Vibe motor",
                FeatureIndex = 0,
                Output = outputJson
            ),
            "1" to DeviceFeatureV4(
                FeatureDescription = "Battery level",
                FeatureIndex = 1,
                Input = inputJson
            )
        )
        val deviceInfo = DeviceInfoV4(
            DeviceName = "HybridDevice",
            DeviceIndex = 7,
            DeviceDisplayName = "Hybrid Device v2",
            DeviceFeatures = features
        )

        val device = ButtplugDevice.fromProtocol(deviceInfo)

        assertEquals(2, device.features.size)
        assertEquals(0, device.features[0].index)
        assertEquals("Vibe motor", device.features[0].description)
        assertEquals(listOf(ActuatorType.Vibrate), device.features[0].outputTypes)

        assertEquals(1, device.features[1].index)
        assertEquals("Battery level", device.features[1].description)
        assertEquals(listOf(SensorType.Battery), device.features[1].inputTypes)

        assertTrue(device.hasActuator(ActuatorType.Vibrate))
        assertTrue(device.hasSensor(SensorType.Battery))
    }
}

// =============================================================================
// DeviceFeature data class tests
// =============================================================================

class DeviceFeatureDataClassTests {

    @Test
    fun `default values are empty for all collection fields`() {
        val feature = DeviceFeature(index = 0)

        assertEquals(0, feature.index)
        assertEquals("", feature.description)
        assertTrue(feature.outputTypes.isEmpty())
        assertTrue(feature.inputTypes.isEmpty())
        assertTrue(feature.stepCount.isEmpty())
        assertTrue(feature.inputCommands.isEmpty())
    }

    @Test
    fun `equality works for identical features`() {
        val a = DeviceFeature(
            index = 0,
            description = "Test",
            outputTypes = listOf(ActuatorType.Vibrate),
            stepCount = mapOf(ActuatorType.Vibrate to 20)
        )
        val b = DeviceFeature(
            index = 0,
            description = "Test",
            outputTypes = listOf(ActuatorType.Vibrate),
            stepCount = mapOf(ActuatorType.Vibrate to 20)
        )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equality differs for different features`() {
        val a = DeviceFeature(index = 0, outputTypes = listOf(ActuatorType.Vibrate))
        val b = DeviceFeature(index = 1, outputTypes = listOf(ActuatorType.Vibrate))

        assertFalse(a == b)
    }
}

// =============================================================================
// ButtplugDevice data class tests
// =============================================================================

class ButtplugDeviceDataClassTests {

    @Test
    fun `default values work correctly`() {
        val device = ButtplugDevice(index = 0, name = "DefaultTest")

        assertEquals(0, device.index)
        assertEquals("DefaultTest", device.name)
        assertEquals("DefaultTest", device.displayName) // defaults to name
        assertEquals(0L, device.messageTimingGap)
        assertTrue(device.features.isEmpty())
    }

    @Test
    fun `equality works for identical devices`() {
        val a = ButtplugDevice(
            index = 1,
            name = "Test",
            displayName = "Test Display",
            messageTimingGap = 100L,
            features = listOf(DeviceFeature(index = 0, outputTypes = listOf(ActuatorType.Vibrate)))
        )
        val b = ButtplugDevice(
            index = 1,
            name = "Test",
            displayName = "Test Display",
            messageTimingGap = 100L,
            features = listOf(DeviceFeature(index = 0, outputTypes = listOf(ActuatorType.Vibrate)))
        )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equality differs for different devices`() {
        val a = ButtplugDevice(index = 0, name = "DeviceA")
        val b = ButtplugDevice(index = 0, name = "DeviceB")

        assertFalse(a == b)
    }
}

// =============================================================================
// ActuatorType enum completeness
// =============================================================================

class ActuatorTypeEnumTests {

    @Test
    fun `all ActuatorType entries are round-trippable through fromName`() {
        for (type in ActuatorType.entries) {
            assertEquals(type, ActuatorType.fromName(type.name))
            assertEquals(type, ActuatorType.fromName(type.name.lowercase()))
            assertEquals(type, ActuatorType.fromName(type.name.uppercase()))
        }
    }
}

// =============================================================================
// SensorType enum completeness
// =============================================================================

class SensorTypeEnumTests {

    @Test
    fun `all SensorType entries are round-trippable through fromName`() {
        for (type in SensorType.entries) {
            assertEquals(type, SensorType.fromName(type.name))
            assertEquals(type, SensorType.fromName(type.name.lowercase()))
            assertEquals(type, SensorType.fromName(type.name.uppercase()))
        }
    }
}

// =============================================================================
// SensorCommand enum completeness
// =============================================================================

class SensorCommandEnumTests {

    @Test
    fun `all SensorCommand entries are round-trippable through fromName`() {
        for (cmd in SensorCommand.entries) {
            assertEquals(cmd, SensorCommand.fromName(cmd.name))
            assertEquals(cmd, SensorCommand.fromName(cmd.name.lowercase()))
            assertEquals(cmd, SensorCommand.fromName(cmd.name.uppercase()))
        }
    }
}
