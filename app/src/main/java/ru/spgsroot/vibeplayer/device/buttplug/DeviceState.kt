package ru.spgsroot.vibeplayer.device.buttplug

sealed interface DeviceState {
    data object Disconnected : DeviceState
    data object Scanning : DeviceState
    data class Connected(val deviceName: String) : DeviceState
    data class Error(val reason: String) : DeviceState
}

data class ButtplugDevice(
    val index: Int,
    val name: String,
    val capabilities: List<String>
)
