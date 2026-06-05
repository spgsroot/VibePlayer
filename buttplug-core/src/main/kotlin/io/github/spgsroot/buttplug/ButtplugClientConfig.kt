package io.github.spgsroot.buttplug

/**
 * Client configuration for the Buttplug connection.
 */
data class ButtplugClientConfig(
    val serverUrl: String = "ws://localhost:12345",
    val clientName: String = "KotlinButtplugClient",
    val protocolVersionMajor: Int = 4,
    val protocolVersionMinor: Int = 0,
    val reconnectDelayMs: Long = 3000L,
    val maxReconnectAttempts: Int = 3,
    val pingIntervalMs: Long = 5000L,
    val requestTimeoutMs: Long = 10_000L,
    val bypassCertVerify: Boolean = true // for self-signed certs on Intiface
) {
    init {
        require(serverUrl.isNotBlank()) { "serverUrl must not be blank" }
        require(clientName.isNotBlank()) { "clientName must not be blank" }
        require(protocolVersionMajor in 0..4) { "protocolVersionMajor must be 0-4" }
        require(reconnectDelayMs > 0) { "reconnectDelayMs must be positive" }
        require(pingIntervalMs > 0) { "pingIntervalMs must be positive" }
    }

    companion object {
        val DEFAULT = ButtplugClientConfig()
    }
}
