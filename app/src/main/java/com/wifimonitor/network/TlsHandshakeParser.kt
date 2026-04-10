package com.wifimonitor.network

import android.util.Log

/**
 * Robust Byte-level TLS Handshake Parser.
 * Extracts:
 * - SNI (Server Name Indication)
 * - TLS Version
 * - Handshake Type
 */
object TlsHandshakeParser {
    private const val TAG = "TlsParser"

    data class HandshakeResult(
        val sni: String?,
        val tlsVersion: String?,
        val handshakeType: String = "Client Hello"
    )

    fun parseClientHello(buffer: ByteArray, length: Int): HandshakeResult? {
        try {
            if (length < 43) return null // Record Header (5) + Handshake Header (4) + Random (32) + ...

            // 1. Check Content Type (0x16 = Handshake)
            if (buffer[0].toInt() != 0x16) return null

            // 2. TLS Version (Record Layer)
            val major = buffer[1].toInt() and 0xFF
            val minor = buffer[2].toInt() and 0xFF
            val recordVersion = "$major.$minor"

            // 3. Handshake Type (0x01 = Client Hello)
            if (buffer[5].toInt() != 0x01) return null

            // 4. Skip to Extensions
            var pos = 38 // Start after Random
            val sessionIdLen = buffer[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen

            val cipherSuiteLen = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherSuiteLen

            val compressMethodLen = buffer[pos].toInt() and 0xFF
            pos += 1 + compressMethodLen

            if (pos + 2 > length) return null
            val extensionsLen = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
            pos += 2

            // 5. Parse Extensions for SNI
            val endPos = (pos + extensionsLen).coerceAtMost(length)
            var sni: String? = null
            
            while (pos + 4 <= endPos) {
                val extType = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
                val extLen = ((buffer[pos + 2].toInt() and 0xFF) shl 8) or (buffer[pos + 3].toInt() and 0xFF)
                pos += 4

                if (pos + extLen > endPos) break // Strict Bounds Check

                if (extType == 0x0000) { // Server Name Extension
                    sni = parseSniExtension(buffer, pos, extLen)
                    break
                }
                pos += extLen
            }

            return HandshakeResult(sni = sni, tlsVersion = recordVersion)
        } catch (e: Exception) {
            Log.e(TAG, "Parsing failed: ${e.message}")
            return null
        }
    }

    private fun parseSniExtension(buffer: ByteArray, start: Int, length: Int): String? {
        try {
            var pos = start + 2 // skip list length
            if (pos >= buffer.size || buffer[pos].toInt() != 0x00) return null // Type = host_name
            pos += 1
            if (pos + 2 > buffer.size) return null
            val nameLen = ((buffer[pos].toInt() and 0xFF) shl 8) or (buffer[pos + 1].toInt() and 0xFF)
            pos += 2
            
            // Audit 9: Constrain extraction to sane limits
            if (nameLen <= 0 || nameLen > 255 || pos + nameLen > buffer.size) return null
            return String(buffer, pos, nameLen, Charsets.US_ASCII)
        } catch (e: Exception) {
            return null
        }
    }
}
