package io.github.spgsroot.buttplug

/**
 * Connection state machine for the Buttplug client.
 */
sealed interface ButtplugClientState {
    /** Not connected to any server */
    data object Disconnected : ButtplugClientState

    /** Attempting to connect */
    data object Connecting : ButtplugClientState

    /** WebSocket connected, performing protocol handshake */
    data object Handshaking : ButtplugClientState

    /** Fully connected and ready to send commands */
    data class Connected(
        val serverName: String,
        val protocolVersionMajor: Int,
        val protocolVersionMinor: Int,
        val maxPingTime: Long
    ) : ButtplugClientState

    /** Device scanning in progress */
    data object Scanning : ButtplugClientState

    /** Reconnecting after disconnection */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ButtplugClientState

    /** Error state with reason */
    data class Error(val reason: String) : ButtplugClientState

    val isConnected: Boolean get() = this is Connected || this is Scanning
}
