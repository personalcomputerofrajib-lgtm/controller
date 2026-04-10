package com.wifimonitor.analyzer

import com.wifimonitor.data.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 8: "Auto Problem Detection" Engine.
 * Converts raw metrics into actionable "Sentinel Solutions".
 */
@Singleton
class NetworkHealthOracle @Inject constructor(
    private val intelligence: IntelligenceEngine
) {

    data class SentinelSolution(
        val title: String,
        val description: String,
        val correctiveAction: String,
        val severity: Int // 0=Info, 1=Warning, 2=Danger
    )

    /**
     * Synthesizes all data points into a "Final Answer".
     */
    fun diagnose(devices: List<NetworkDevice>, pressureScore: Int): List<SentinelSolution> {
        val solutions = mutableListOf<SentinelSolution>()

        // 1. High Latency Root Cause
        val highLatencyDevices = devices.filter { it.status == DeviceStatus.ONLINE && it.pingResponseMs > 250 }
        val heavyUploaders = devices.filter { it.uploadRateMbps > 2.0f }
        
        if (highLatencyDevices.isNotEmpty() && heavyUploaders.isNotEmpty()) {
            val names = heavyUploaders.joinToString { it.displayName }
            solutions.add(SentinelSolution(
                "Congestion Root Cause",
                "High latency is likely caused by $names performing a heavy upload or sync.",
                "Throttling or pausing background sync on $names will restore latency.",
                severity = 2
            ))
        }

        // 2. Saturation Check
        if (pressureScore > 85) {
            solutions.add(SentinelSolution(
                "Network Saturated",
                "Your network has reached its estimated comfortable capacity with ${devices.count { it.status == DeviceStatus.ONLINE }} active nodes.",
                "Review 'Shadow Devices' to free up bandwidth.",
                severity = 1
            ))
        }

        // 3. Unknown Intruder Alert (Contextual)
        val unknownActives = devices.filter { !it.isKnown && it.status == DeviceStatus.ONLINE }
        if (unknownActives.isNotEmpty()) {
            solutions.add(SentinelSolution(
                "Unrecognized Bandwidth Usage",
                "An unknown device is currently online and consuming network resources.",
                "Identify and Tag '${unknownActives.first().displayName}' to dismiss security warning.",
                severity = 1
            ))
        }

        // 4. ISP Bottleneck Inference
        val collectiveLat = devices.filter { it.status == DeviceStatus.ONLINE }.map { it.pingResponseMs }.average()
        if (collectiveLat > 300 && heavyUploaders.isEmpty()) {
            solutions.add(SentinelSolution(
                "External Bottleneck",
                "Collective ping spike detected across ALL devices despite low local traffic.",
                "This indicates an ISP issue or Router backbone failure (Local Loop issue).",
                severity = 1
            ))
        }

        return solutions
    }
}
