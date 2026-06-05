package io.github.spgsroot.buttplug.connection

import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/**
 * OkHttp WebSocket transport for Buttplug protocol.
 */
class WebSocketTransport(
    private val bypassCertVerify: Boolean = true
) : ButtplugTransport {

    private var webSocket: WebSocket? = null
    @Volatile private var _isConnected: Boolean = false
    private var listener: TransportListener? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for persistent connection
            .also { builder ->
                if (bypassCertVerify) {
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    })
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    builder.hostnameVerifier { _, _ -> true }
                }
            }
            .build()
    }

    override val isConnected: Boolean get() = _isConnected

    override fun connect(url: String, listener: TransportListener) {
        this.listener = listener
        val request = Request.Builder().url(url).build()
        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@WebSocketTransport.webSocket = webSocket
                _isConnected = true
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected = false
                this@WebSocketTransport.webSocket = null
                listener.onDisconnected(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected = false
                this@WebSocketTransport.webSocket = null
                listener.onError(t)
            }
        })
    }

    override fun send(message: String) {
        webSocket?.send(message) ?: throw IllegalStateException("WebSocket not connected")
    }

    override fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected = false
    }
}
