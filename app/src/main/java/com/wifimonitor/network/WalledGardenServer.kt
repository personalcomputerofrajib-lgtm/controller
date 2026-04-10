package com.wifimonitor.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 11 Production Component.
 * Minimal HTTP server that hosts the "Access Restricted" landing page.
 * Blocked devices are redirected here via the DnsServer.
 */
@Singleton
class WalledGardenServer @Inject constructor() {
    private val tag = "WalledGardenServer"
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isRunning = false

    fun start(port: Int = 8081) { // Audit 13: Standardized non-root high port
        if (isRunning) return
        isRunning = true
        job = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.i(tag, "Walled Garden Dashboard started on port $port")
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.e(tag, "Server error: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        job?.cancel()
    }

    private fun handleClient(socket: java.net.Socket) {
        socket.use { client ->
            try {
                // Audit 9: TLS Detection (Prevent hanging on HTTPS redirects)
                val inputStream = client.getInputStream()
                val peek = inputStream.read()
                if (peek == 0x16) { // TLS Handshake record
                    Log.w(tag, "Blocked HTTPS attempt from ${client.inetAddress.hostAddress} — Redirecting to plaintext dashboard")
                    return // socket.use will close
                }

                val output = client.getOutputStream()
                val html = """
                    <html>
                    <head>
                        <title>Access Restricted</title>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body { background: #0A0E1A; color: #E0E6ED; font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }
                            .card { background: #151B2D; padding: 40px; border-radius: 20px; border: 1px solid #1FBABF; box-shadow: 0 0 20px rgba(31, 186, 191, 0.2); max-width: 400px; }
                            h1 { color: #FF9F0A; margin: 0 0 20px; font-size: 24px; }
                            p { color: #94A3B8; line-height: 1.6; }
                            .brand { border-top: 1px solid #2D3748; margin-top: 30px; padding-top: 20px; font-size: 12px; color: #1FBABF; font-weight: bold; text-transform: uppercase; letter-spacing: 2px; }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h1>⚠️ Access Restricted</h1>
                            <p>This device is currently under a parental control schedule or has been paused by the network administrator.</p>
                            <p>Please contact your administrator to request access.</p>
                            <div class="brand">Autonomous Network Intelligence</div>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                val response = "HTTP/1.1 200 OK\r\n" +
                               "Content-Type: text/html\r\n" +
                               "Content-Length: ${html.length}\r\n" +
                               "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                               "Connection: close\r\n\r\n" +
                               html

                output.write(response.toByteArray())
                output.flush()
            } catch (_: Exception) {}
        }
    }
}
