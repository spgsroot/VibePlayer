package io.github.spgsroot.buttplug.connection

/**
 * Abstraction over the underlying transport mechanism.
 * Allows swapping WebSocket for in-process or mock transports.
 */
interface ButtplugTransport {
    /** Connect to the server. Non-blocking - callbacks via listener. */
    fun connect(url: String, listener: TransportListener)

    /** Send a raw text message (JSON string). */
    fun send(message: String)

    /** Close the connection gracefully. */
    fun disconnect()

    /** Whether the transport is currently connected. */
    val isConnected: Boolean
}

/**
 * Listener for transport events.
 */
interface TransportListener {
    fun onConnected()
    fun onMessage(text: String)
    fun onDisconnected(code: Int, reason: String)
    fun onError(error: Throwable)
}
