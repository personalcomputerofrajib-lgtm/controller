package com.wifimonitor.scanner

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

data class DnsQuery(
    val domain: String,
    val sourceIp: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class DnsMonitor @Inject constructor() {
    private val tag = "DnsMonitor"

    @Volatile
    private var isRunning = false
    private var socket: DatagramSocket? = null
    private var mcastSocket: java.net.MulticastSocket? = null

    /**
     * Passively listens for DNS traffic on the network.
     *
     * Note: On Android, binding to port 53 requires no special permission beyond INTERNET.
     * However, we can only capture traffic *destined for our device* (DNS responses) unless
     * the device is acting as a DNS proxy. We emit any DNS query domains we observe.
     *
     * For a fuller picture, we listen on multicast DNS (port 5353) which IS multicast
     * and thus visible to all devices on the LAN.
     */
    suspend fun startCapture(onQuery: (DnsQuery) -> Unit) {
        isRunning = true
        withContext(Dispatchers.IO) {
            // Listen on mDNS multicast (224.0.0.251:5353) — visible to all LAN devices
            try {
                val multicastGroup = InetAddress.getByName("224.0.0.251")
                mcastSocket = java.net.MulticastSocket(5353).apply {
                    joinGroup(multicastGroup)
                    soTimeout = 1000
                }

                val buffer = ByteArray(512)
                Log.i(tag, "DNS monitor started on mDNS multicast")

                while (isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        mcastSocket?.receive(packet)
                        val sourceIp = packet.address.hostAddress ?: "?"
                        val domain = parseDnsQuery(packet.data, packet.length)
                        if (domain != null && domain.isNotBlank()) {
                            Log.d(tag, "DNS query from $sourceIp: $domain")
                            onQuery(DnsQuery(domain, sourceIp))
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Expected — keep looping
                    } catch (e: Exception) {
                        if (isRunning) Log.w(tag, "mDNS receive error: ${e.message}")
                    }
                }
                try { mcastSocket?.leaveGroup(multicastGroup) } catch (e: Exception) {}
                mcastSocket?.close()
                mcastSocket = null
            } catch (e: Exception) {
                Log.e(tag, "Failed to start mDNS monitor: ${e.message}")
            }
        }
    }

    fun stopCapture() {
        isRunning = false
        try { socket?.close() } catch (e: Exception) {}
        try { mcastSocket?.close() } catch (e: Exception) {}
    }

    /**
     * Minimal DNS packet parser — extracts the queried domain name from a DNS query packet.
     * DNS wire format: 12 byte header, then question section with QNAME labels.
     */
    private fun parseDnsQuery(data: ByteArray, length: Int): String? {
        return try {
            if (length < 12) return null
            // Check QR bit (bit 15 of flags) — 0 = query, 1 = response
            val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val isQuery = (flags and 0x8000) == 0
            if (!isQuery) return null

            val sb = StringBuilder()
            var i = 12  // start of question section
            var jumpCount = 0
            var p = i
            
            while (p < length && jumpCount < 5) {
                val labelLen = data[p].toInt() and 0xFF
                if (labelLen == 0) break
                
                // Audit 14: Follow DNS Compression Pointers (0xC0)
                if ((labelLen and 0xC0) == 0xC0) {
                    if (p + 1 >= length) break
                    p = ((labelLen and 0x3F) shl 8) or (data[p + 1].toInt() and 0xFF)
                    jumpCount++
                    continue
                }
                
                if (p + labelLen + 1 > length) break
                if (sb.isNotEmpty()) sb.append(".")
                sb.append(String(data, p + 1, labelLen, Charsets.US_ASCII))
                p += labelLen + 1
            }
            val domain = sb.toString()
            if (domain.isNotBlank()) domain else null
        } catch (e: Exception) {
            null
        }
    }
}
