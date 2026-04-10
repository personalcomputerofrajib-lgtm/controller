package com.wifimonitor.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight Infrastructure Proxy for Deep Packet Inspection (DPI).
 * Listens for TCP connections and extracts SNI (Server Name Indication) 
 * from TLS handshakes to identify encrypted traffic destinations.
 */
@Singleton
class ProxyServer @Inject constructor(
    private val flowTracker: com.wifimonitor.analyzer.FlowTracker,
    private val repository: com.wifimonitor.data.DeviceRepository
) {
    private val tag = "ProxyServer"
    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isRunning = false

    fun start(port: Int = 8080) {
        if (isRunning) return
        isRunning = true
        proxyJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.i(tag, "DPI Proxy Server started on port $port")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e(tag, "Proxy Server error: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        proxyJob?.cancel()
    }

    private suspend fun handleClient(clientSocket: Socket) {
        clientSocket.use { client ->
            try {
                val inputStream = client.getInputStream()
                val peekBuffer = ByteArray(1024)
                val bytesRead = inputStream.read(peekBuffer)
                
                if (bytesRead > 0) {
                    // ── TWO-MODE DPI (Audits 10-12) ──
                    
                    // Mode A: TLS SNI Extraction
                    val tlsResult = TlsHandshakeParser.parseClientHello(peekBuffer, bytesRead)
                    val sni = tlsResult?.sni
                    
                    // Mode B: HTTP Host Extraction (Port 80 Fallback)
                    val host = sni ?: HttpHeaderParser.parseHost(peekBuffer, bytesRead)
                    
                    if (host != null) {
                        Log.i(tag, "DPI Identified: $host from ${client.inetAddress.hostAddress}")
                        flowTracker.recordFlow(
                            sourceIp = client.inetAddress.hostAddress,
                            destIp = host, 
                            destPort = if (sni != null) 443 else 80,
                            sni = sni
                        )
                        
                        // ── TWO-WAY BRIDGE ──
                        bridgeTraffic(client, host, if (sni != null) 443 else 80, peekBuffer, bytesRead)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Client error: ${e.message}")
            }
        }
    }

    private suspend fun bridgeTraffic(client: Socket, host: String, port: Int, initialData: ByteArray, initialLen: Int) {
        withContext(Dispatchers.IO) {
            try {
                val remote = Socket(host, port)
                remote.soTimeout = 10000 // Quality #17: Prevent hung threads
                
                remote.use { dest ->
                    val destOut = dest.getOutputStream()
                    val destIn = dest.getInputStream()
                    val clientIn = client.getInputStream()
                    val clientOut = client.getOutputStream()

                    // Send initial data (TLS ClientHello) to destination
                    destOut.write(initialData, 0, initialLen)
                    destOut.flush()

                    // Launch bidirectional pipes
                    val c2d = launch { 
                        pipe(clientIn, destOut, client.inetAddress.hostAddress, isUpstream = true) 
                    }
                    val d2c = launch { 
                        pipe(destIn, clientOut, client.inetAddress.hostAddress, isUpstream = false) 
                    }

                    c2d.join()
                    d2c.join()
                }
            } catch (e: Exception) {
                Log.e(tag, "Bridge failed to $host: ${e.message}")
            }
        }
    }

    private suspend fun pipe(input: InputStream, output: OutputStream, ip: String, isUpstream: Boolean) {
        // Audit 8: Local buffer for this session to minimize GC
        val buffer = ByteArray(16384)
        var totalBytes = 0L
        try {
            var read: Int
            while (isActive && input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                
                totalBytes += read
                if (totalBytes > 102400) { // Every 100KB
                    if (isUpstream) repository.addDeviceTrafficIncrement(ip, 0, totalBytes)
                    else repository.addDeviceTrafficIncrement(ip, totalBytes, 0)
                    totalBytes = 0
                }
            }
        } catch (_: Exception) {} 
        finally {
            if (totalBytes > 0) {
                if (isUpstream) repository.addDeviceTrafficIncrement(ip, 0, totalBytes)
                else repository.addDeviceTrafficIncrement(ip, totalBytes, 0)
            }
        }
    }

    // SNI Extraction is now handled by the robust TlsHandshakeParser
}
