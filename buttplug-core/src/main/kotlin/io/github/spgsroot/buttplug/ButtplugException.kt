package io.github.spgsroot.buttplug

import io.github.spgsroot.buttplug.protocol.ButtplugErrorCode

/**
 * Exception thrown for Buttplug protocol errors.
 */
sealed class ButtplugException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(message: String, cause: Throwable? = null) : ButtplugException(message, cause)
    class HandshakeFailed(message: String) : ButtplugException(message)
    class ProtocolError(val errorCode: ButtplugErrorCode, message: String) : ButtplugException(message)
    class DeviceNotFound(val deviceIndex: Int) : ButtplugException("Device not found: $deviceIndex")
    class FeatureNotFound(val deviceIndex: Int, val featureIndex: Int) :
        ButtplugException("Feature $featureIndex not found on device $deviceIndex")

    class NotConnected : ButtplugException("Not connected to Buttplug server")
    class Timeout(message: String) : ButtplugException(message)
    class Disconnected(message: String = "Disconnected from server") : ButtplugException(message)
}
