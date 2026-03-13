package ru.spgsroot.vibeplayer.device.buttplug

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class ButtplugMessage {
    @Serializable
    @SerialName("ServerInfo")
    data class ServerInfo(
        @SerialName("ServerName") val serverName: String,
        @SerialName("MessageVersion") val messageVersion: Int = 3
    ) : ButtplugMessage()

    @Serializable
    @SerialName("DeviceAdded")
    data class DeviceAdded(
        @SerialName("DeviceName") val deviceName: String,
        @SerialName("DeviceIndex") val deviceIndex: Int,
        @SerialName("DeviceMessages") val deviceMessages: Map<String, JsonObject>
    ) : ButtplugMessage()

    @Serializable
    @SerialName("DeviceRemoved")
    data class DeviceRemoved(
        @SerialName("DeviceIndex") val deviceIndex: Int
    ) : ButtplugMessage()

    @Serializable
    @SerialName("Ok")
    data class Ok(
        @SerialName("Id") val id: Int
    ) : ButtplugMessage()

    @Serializable
    @SerialName("Error")
    data class Error(
        @SerialName("Id") val id: Int,
        @SerialName("ErrorMessage") val errorMessage: String
    ) : ButtplugMessage()
}
