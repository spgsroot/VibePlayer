package io.github.spgsroot.buttplug.device

import io.github.spgsroot.buttplug.protocol.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the device list state, updated from server messages.
 */
class DeviceManager {
    private val _devices = MutableStateFlow<List<ButtplugDevice>>(emptyList())
    val devices: StateFlow<List<ButtplugDevice>> = _devices.asStateFlow()

    fun handleDeviceList(deviceList: DeviceList) {
        _devices.value = deviceList.Devices.map { ButtplugDevice.fromProtocol(it) }
            .sortedBy { it.index }
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
