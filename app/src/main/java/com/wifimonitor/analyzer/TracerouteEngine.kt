package com.wifimonitor.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TracerouteEngine @Inject constructor() {

    data class Hop(
        val ttl: Int,
        val ip: String,
        val latencyMs: Double,
        val isFinal: Boolean
    )

    suspend fun runTraceroute(host: String, onHop: (Hop) -> Unit) = withContext(Dispatchers.IO) {
        val maxHops = 30
        for (ttl in 1..maxHops) {
            val hop = probeHop(host, ttl)
            onHop(hop)
            if (hop.isFinal || hop.ttl >= maxHops) break
        }
    }

    private fun probeHop(host: String, ttl: Int): Hop {
        return try {
            // -c 1: one packet, -W 1: 1s timeout, -t: set TTL
            val process = Runtime.getRuntime().exec("ping -c 1 -W 2 -t $ttl $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var hopIp = "* * *"
            var latency = 0.0
            var isFinal = false

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: ""
                // Case 1: Hop identification (Time to live exceeded)
                if (currentLine.contains("From", ignoreCase = true)) {
                    hopIp = extractIp(currentLine)
                }
                // Case 2: Destination reached
                if (currentLine.contains("time=", ignoreCase = true)) {
                    hopIp = extractIp(currentLine)
                    latency = extractLatency(currentLine)
                    isFinal = true
                }
            }
            process.waitFor()
            Hop(ttl, hopIp, latency, isFinal)
        } catch (e: Exception) {
            Hop(ttl, "* * *", 0.0, false)
        }
    }

    private fun extractIp(line: String): String {
        val regex = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""".toRegex()
        return regex.find(line)?.value ?: "* * *"
    }

    private fun extractLatency(line: String): Double {
        val regex = """time=([\d.]+)""".toRegex()
        return regex.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }
}
