package com.wifimonitor.scanner

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Level 9 Precision Component.
 * Implements NetBIOS Name Service (NBNS) probing over UDP 137.
 * Used to resolve hostnames for Windows and legacy enterprise devices 
 * that do not support mDNS.
 */
object NetBiosProber {
    private const val TAG = "NetBiosProber"
    private const val PORT = 137
    private const val TIMEOUT = 1000

    /**
     * Attempts to resolve the NetBIOS name for the given IP.
     */
    suspend fun resolveName(ip: String): String? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = TIMEOUT
            
            // NetBIOS Name Query Request
            // Transaction ID: 0x8000
            // Flags: 0x0110 (Iterative, Broadcast)
            // Questions: 1
            // Query Name: * (CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA)
            val query = byteArrayOf(
                0x80.toByte(), 0x00.toByte(), // ID
                0x00.toByte(), 0x00.toByte(), // Flags
                0x00.toByte(), 0x01.toByte(), // Questions
                0x00.toByte(), 0x00.toByte(), // Answer RRs
                0x00.toByte(), 0x00.toByte(), // Authority RRs
                0x00.toByte(), 0x00.toByte(), // Additional RRs
                // Scope name: * (encoded as CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA)
                0x20.toByte(),
                0x43.toByte(), 0x4b.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
                0x41.toByte(), 0x41.toByte(), 0x00.toByte(),
                0x00.toByte(), 0x21.toByte(), // Type: NBSTAT
                0x00.toByte(), 0x01.toByte()  // Class: IN
            )
            
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(query, query.size, address, PORT)
            socket.send(packet)
            
            val responseBuf = ByteArray(1024)
            val responsePacket = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(responsePacket)
            
            parseNetBiosName(responsePacket.data, responsePacket.length)
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun parseNetBiosName(data: ByteArray, length: Int): String? {
        try {
            // NetBIOS parsing logic
            // Offset 56 is usually where the names start in a Node Status Response
            if (length < 57) return null
            val numNames = data[56].toInt() and 0xFF
            var offset = 57
            
            for (i in 0 until numNames) {
                val name = String(data, offset, 15, Charsets.US_ASCII).trim()
                val type = data[offset + 15].toInt() and 0xFF
                // Type 0x00 or 0x20 are usually hostnames
                if (type == 0x00 || type == 0x20) {
                    return name
                }
                offset += 18
            }
        } catch (_: Exception) {}
        return null
    }
}
