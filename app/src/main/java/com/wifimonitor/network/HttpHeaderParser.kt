package com.wifimonitor.network

/**
 * Lightweight HTTP Host Header Extractor.
 * Extracts the destination domain from unencrypted Port 80 traffic.
 * Used for deep packet inspection on non-TLS flows.
 */
object HttpHeaderParser {

    /**
     * Parse HTTP Request to extract the 'Host' header.
     */
    fun parseHost(buffer: ByteArray, length: Int): String? {
        try {
            val request = String(buffer, 0, length.coerceAtMost(2048), Charsets.US_ASCII)
            val lines = request.split("\r\n")
            if (lines.isEmpty()) return null

            // Find Host: header
            for (line in lines) {
                if (line.startsWith("Host:", ignoreCase = true)) {
                    return line.substring(5).trim().split(":").first()
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
