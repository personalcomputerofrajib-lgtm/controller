package com.wifimonitor.analyzer

import android.util.Log
import kotlinx.coroutines.*
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IspDiagnosticEngine @Inject constructor(
    private val diagnostics: DiagnosticLogger
) {
    private val tag = "IspDiagnostic"
    
    data class IspStatus(
        val latencyMs: Int,
        val jitterMs: Int,
        val stabilityPct: Int,
        val statusMessage: String,
        val isBackboneHealthy: Boolean
    )

    private val backboneHosts = listOf("1.1.1.1", "8.8.8.8")
    private val history = mutableListOf<Int>()
    private val maxHistory = 20

    /**
     * Probes the global backbone to assess ISP health.
     */
    suspend fun probeIspHealth(): IspStatus = withContext(Dispatchers.IO) {
        var totalLatency = 0
        var successCount = 0
        
        for (host in backboneHosts) {
            val start = System.nanoTime()
            try {
                val addr = InetAddress.getByName(host)
                if (addr.isReachable(1000)) {
                    val latency = ((System.nanoTime() - start) / 1_000_000).toInt()
                    totalLatency += latency
                    successCount++
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to probe $host: ${e.message}")
            }
        }

        if (successCount == 0) {
            return@withContext IspStatus(0, 0, 0, "ISP Offline / Massive Packet Loss", false)
        }

        val avgLatency = totalLatency / successCount
        history.add(avgLatency)
        if (history.size > maxHistory) history.removeAt(0)

        val jitter = if (history.size > 1) {
            history.zipWithNext { a, b -> Math.abs(a - b) }.average().toInt()
        } else 0

        val isHealthy = avgLatency < 150 && jitter < 30
        val message = when {
            avgLatency > 300 -> "Severe ISP Latency Detected"
            jitter > 50 -> "ISP Jitter Spike - Unstable Path"
            !isHealthy -> "Degraded Backbone Connectivity"
            else -> "ISP Backbone Nominal"
        }

        IspStatus(avgLatency, jitter, (successCount * 100 / backboneHosts.size), message, isHealthy)
    }

    /**
     * Determines if a network issue is local or global.
     */
    fun analyzeBottleneck(localLatency: Int, isIspHealthy: Boolean): String {
        return when {
            localLatency > 100 && isIspHealthy -> "Bottleneck: Internal WiFi / Router Stress"
            localLatency < 50 && !isIspHealthy -> "Bottleneck: ISP Level / External Backbone"
            localLatency > 100 && !isIspHealthy -> "Bottleneck: Combined (Internal + External)"
            else -> "Network Path Clear"
        }
    }
}
