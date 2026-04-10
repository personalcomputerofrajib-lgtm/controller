package com.wifimonitor.network

import android.util.Log
import com.wifimonitor.rules.RuleEngine
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight DNS Authority Server.
 * Listens for UDP DNS queries and evaluates them against the RuleEngine.
 * Can block queries by returning NXDOMAIN or redirecting to a walled garden.
 */
@Singleton
class DnsServer @Inject constructor(
    private val ruleEngine: RuleEngine,
    private val macResolver: IpToMacResolver,
    private val repository: com.wifimonitor.data.DeviceRepository,
    private val interfaceMonitor: com.wifimonitor.analyzer.NetworkInterfaceMonitor
) {
    private val tag = "DnsServer"
    private var socket: DatagramSocket? = null
    private var upstreamSocket: DatagramSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isRunning = false

    /**
     * Start the DNS server. 
     * @param port The port to listen on. Default 5354 (Android non-root safe).
     */
    fun start(port: Int = 5354) {
        if (isRunning) return
        isRunning = true
        serverJob = scope.launch {
            try {
                socket = DatagramSocket(port)
                upstreamSocket = DatagramSocket().apply {
                    soTimeout = 1500 // Audit 5: Defensive Upstream Timeout
                }
                val buffer = ByteArray(2048) // Larger buffer
                Log.i(tag, "DNS Authority Server started on port $port")

                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket?.receive(packet)
                        val queryData = packet.data.copyOf(packet.length)
                        val queryPacket = DatagramPacket(queryData, queryData.size, packet.address, packet.port)
                        handleQuery(queryPacket)
                    } catch (e: Exception) {
                        if (isRunning) Log.e(tag, "Receive error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start DNS server: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        socket?.close()
        upstreamSocket?.close()
        serverJob?.cancel()
    }

    private fun handleQuery(packet: DatagramPacket) {
        val data = packet.data
        val length = packet.length
        val sourceIp = packet.address.hostAddress ?: "Unknown"

        // Basic DNS parsing to extract domain
        val domain = parseDomain(data, length) ?: return
        
        // ── Principle #11: MAC-Based Enforcement ──
        val sourceMac = macResolver.resolve(sourceIp) ?: "Unknown"
        val action = ruleEngine.checkDomainAccess(sourceMac, domain)

        if (action == RuleEngine.Action.BLOCK) {
            Log.w(tag, "Redirecting $domain for $sourceMac to Walled Garden")
            logQuery(sourceMac, domain, true)
            
            // ── Principle #11: Redirect to Walled Garden ──
            val localIp = interfaceMonitor.state.value.activeInterface?.ipAddress
            if (localIp != null) {
                sendARecordResponse(packet, localIp)
            } else {
                sendNxDomain(packet)
            }
        } else {
            logQuery(sourceMac, domain, false)
            proxyQueryToUpstream(packet, domain)
        }
    }

    private fun logQuery(mac: String, domain: String, isBlocked: Boolean) {
        scope.launch {
            repository.addTrafficRecord(com.wifimonitor.data.TrafficRecord(
                deviceMac = mac,
                domain = domain,
                source = "DNS Authority",
                isBlocked = isBlocked
            ))
        }
    }

    private fun parseDomain(data: ByteArray, length: Int): String? {
        if (length < 12) return null
        val sb = StringBuilder()
        var i = 12
        while (i < length) {
            val labelLen = data[i].toInt() and 0xFF
            if (labelLen == 0) break
            if (i + labelLen + 1 > length) break
            if (sb.isNotEmpty()) sb.append(".")
            sb.append(String(data, i + 1, labelLen, Charsets.US_ASCII))
            i += labelLen + 1
        }
        return sb.toString()
    }

    private fun sendNxDomain(packet: DatagramPacket) {
        val data = packet.data
        if (packet.length < 12) return

        // Create NXDOMAIN response header
        val response = data.copyOf(packet.length)
        response[2] = (response[2].toInt() or 0x81).toByte() 
        response[3] = (response[3].toInt() or 0x83).toByte() 
        socket?.send(DatagramPacket(response, response.size, packet.address, packet.port))
    }

    private fun sendARecordResponse(packet: DatagramPacket, ip: String) {
        try {
            val data = packet.data
            val response = ByteArray(512)
            System.arraycopy(data, 0, response, 0, packet.length)

            // Header: QR=1, AA=1, RCODE=0, ANCOUNT=1
            response[2] = (response[2].toInt() or 0x84).toByte() 
            response[3] = (response[3].toInt() or 0x00).toByte() 
            response[6] = 0; response[7] = 1 // Answer count = 1

            // Answer section starts at packet.length
            var pos = packet.length
            
            // Name (Offset to question name)
            response[pos++] = 0xc0.toByte(); response[pos++] = 0x0c.toByte()
            
            // Type A (1), Class IN (1)
            response[pos++] = 0; response[pos++] = 1
            response[pos++] = 0; response[pos++] = 1
            
            // TTL: 60s
            response[pos++] = 0; response[pos++] = 0; response[pos++] = 0; response[pos++] = 60
            
            // Data length: 4 bytes
            response[pos++] = 0; response[pos++] = 4
            
            // IP address
            val ipBytes = InetAddress.getByName(ip).address
            System.arraycopy(ipBytes, 0, response, pos, 4)
            pos += 4

            socket?.send(DatagramPacket(response, pos, packet.address, packet.port))
        } catch (_: Exception) {
            sendNxDomain(packet)
        }
    }

    private fun proxyQueryToUpstream(packet: DatagramPacket, domain: String) {
        scope.launch {
            try {
                val upstreamAddress = InetAddress.getByName("8.8.8.8")
                val queryPacket = DatagramPacket(packet.data, packet.length, upstreamAddress, 53)
                
                upstreamSocket?.send(queryPacket)
                
                val responseBuf = ByteArray(1024)
                val responsePacket = DatagramPacket(responseBuf, responseBuf.size)
                
                // Note: In high-concurrency, we'd need to match transaction IDs.
                // For this stabilization pass, we use the reused socket to prevent leak.
                upstreamSocket?.receive(responsePacket)
                
                val clientResponse = DatagramPacket(responsePacket.data, responsePacket.length, packet.address, packet.port)
                socket?.send(clientResponse)
            } catch (e: Exception) {
                Log.e(tag, "Upstream proxy failed for $domain: ${e.message}")
                sendNxDomain(packet)
            }
        }
    }
}
