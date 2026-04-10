package com.wifimonitor.analyzer

import com.wifimonitor.data.ActivityLevel
import com.wifimonitor.data.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Killer Feature #1: Root Cause Analysis.
 * Analyzes the network ecosystem to definitively attribute slowness/health drops.
 */
@Singleton
class RootCauseAnalyzer @Inject constructor() {

    sealed class Diagnosis {
        object Healthy : Diagnosis()
        data class Congestion(val sourceDevice: NetworkDevice?, val intensity: Float) : Diagnosis()
        data class EnvironmentStress(val averageJitter: Int) : Diagnosis()
        data class DeviceInstability(val device: NetworkDevice) : Diagnosis()
        data class PeakLoad(val activeCount: Int) : Diagnosis()
    }

    fun analyze(devices: List<NetworkDevice>): Diagnosis {
        val online = devices.filter { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }
        if (online.isEmpty()) return Diagnosis.Healthy

        // 1. Detect Environmental Stress (Aggregate Jitter)
        val avgJitter = online.map { it.jitterMs }.average()
        if (avgJitter > 50) return Diagnosis.EnvironmentStress(avgJitter.toInt())

        // 2. Detect Single Point Congestion
        val heavyHitter = online.maxByOrNull { it.activityLevel.ordinal }
        if (heavyHitter?.activityLevel == ActivityLevel.HEAVY || heavyHitter?.activityLevel == ActivityLevel.ACTIVE) {
            // Check if this device is significantly busier than others
            val othersAvg = online.filter { it.mac != heavyHitter.mac }.map { it.activityLevel.ordinal }.average()
            if (heavyHitter.activityLevel.ordinal > othersAvg + 1.5) {
                return Diagnosis.Congestion(heavyHitter, (heavyHitter.activityLevel.ordinal / 4f))
            }
        }

        // 3. Detect Targeted Instability
        val unstable = online.filter { it.jitterMs > 60 || it.reliabilityPct < 70 }
        if (unstable.size == 1) return Diagnosis.DeviceInstability(unstable.first())

        // 4. Detect Peak Load (Crowded Network)
        if (online.size >= 8) return Diagnosis.PeakLoad(online.size)

        return Diagnosis.Healthy
    }

    fun toNarrative(diagnosis: Diagnosis): String {
        return when (diagnosis) {
            is Diagnosis.Healthy -> "All systems nominal. Balanced load."
            is Diagnosis.Congestion -> "Congestion caused by heavy traffic from ${diagnosis.sourceDevice?.displayName ?: "Unknown Device"}."
            is Diagnosis.EnvironmentStress -> "External stress detected. High aggregate jitter (${diagnosis.averageJitter}ms) across the network."
            is Diagnosis.DeviceInstability -> "Localized instability on ${diagnosis.device.displayName}. Check proximity to router."
            is Diagnosis.PeakLoad -> "Network is crowded with ${diagnosis.activeCount} active devices."
        }
    }
}
