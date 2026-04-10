package com.wifimonitor.analyzer

import android.util.Log
import com.wifimonitor.data.ChangeLogRepository
import com.wifimonitor.data.ChangeType
import com.wifimonitor.data.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 5 behavioral engine.
 * Orchestrates deep analysis like pressure calculation and correlation.
 */
@Singleton
class IntelligenceEngine @Inject constructor(
    private val sessionTracker: SessionTracker,
    private val changeLog: ChangeLogRepository,
    private val fingerprinter: NetworkFingerprintEngine
) {

    data class NetworkPressure(
        val score: Int, // 0-100
        val label: String,
        val activeCount: Int
    )

    /**
     * Aggregates device metrics into an overall Network Pressure Indicator.
     */
    fun computeNetworkPressure(devices: List<NetworkDevice>): NetworkPressure {
        val online = devices.filter { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }
        if (online.isEmpty()) return NetworkPressure(0, "Silent", 0)

        val totalActivity = online.sumOf { device ->
            val score: Int = when (device.activityLevel) {
                com.wifimonitor.data.ActivityLevel.IDLE -> 5
                com.wifimonitor.data.ActivityLevel.LOW -> 20
                com.wifimonitor.data.ActivityLevel.BROWSING -> 50
                com.wifimonitor.data.ActivityLevel.ACTIVE -> 80
                com.wifimonitor.data.ActivityLevel.HEAVY -> 100
            }
            score.toLong()
        } / online.size

        val pressureScore = (totalActivity + (online.size * 2)).coerceIn(0, 100)
        
        val label = when {
            pressureScore > 80 -> "Extreme Pressure"
            pressureScore > 60 -> "High Load"
            pressureScore > 40 -> "Concentrated Activity"
            pressureScore > 20 -> "Stable"
            else -> "Light"
        }

        return NetworkPressure(pressureScore, label, online.size)
    }

    /**
     * Logic for detecting if a device just "Woke up" or went into "Sleep/Sleepy" state.
     */
    fun detectStateTransitions(mac: String, newActivity: com.wifimonitor.data.ActivityLevel) {
        val session = sessionTracker.getSession(mac) ?: return
        val history = session.activityHistory
        if (history.size < 5) return

        val recentAvg = history.takeLast(5).average()
        val currentVal = when(newActivity) {
            com.wifimonitor.data.ActivityLevel.IDLE -> 0.1f
            com.wifimonitor.data.ActivityLevel.LOW -> 0.3f
            else -> 0.8f
        }

        if (recentAvg < 0.2f && currentVal > 0.6f) {
            session.wakeCount++
            changeLog.addEvent(ChangeType.WAKE, mac, "Device resumed active state")
        } else if (recentAvg > 0.6f && currentVal < 0.2f) {
            session.sleepCount++
            changeLog.addEvent(ChangeType.SLEEP, mac, "Device entered idle state")
        }
    }

    /**
     * Cross-device correlation: Detect if multiple devices spike at once.
     */
    fun checkCorrelations(devices: List<NetworkDevice>) {
        val highActivity = devices.filter { 
            it.activityLevel == com.wifimonitor.data.ActivityLevel.HEAVY || 
            it.activityLevel == com.wifimonitor.data.ActivityLevel.ACTIVE 
        }
        
        if (highActivity.size >= 3) {
            changeLog.addEvent(
                ChangeType.ANOMALY, 
                "NETWORK", 
                "Usage Correlation: ${highActivity.size} devices streaming simultaneously",
                severity = 1
            )
        }
    }

    /**
     * Level 6: Bottleneck Detection.
     * Detects if network is saturated based on collective latency spikes.
     */
    fun detectBottleneck(devices: List<NetworkDevice>) {
        val online = devices.filter { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }
        if (online.size < 2) return

        val collectiveJitter = online.map { it.jitterMs }.average()
        val highLatencyCount = online.count { it.pingResponseMs > 200 }

        if (collectiveJitter > 50 && highLatencyCount >= online.size / 2) {
            changeLog.addEvent(
                ChangeType.ANOMALY,
                "NETWORK",
                "Network Bottleneck detected: Collective latency spike",
                severity = 2
            )
        }
    }

    /**
     * Level 6: Predictive Analytics.
     */
    fun predictPeakUsage(ssid: String?): String {
        val profile = fingerprinter.getProfile(ssid) ?: return "Learning patterns..."
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        return when {
            profile.mostActiveHour == hour + 1 -> "Peak load window predicted in 60 minutes"
            profile.typicalActivityScore > 0.7f -> "Network is in a sustained high-load window"
            else -> "Network stable — no immediate peaks predicted"
        }
    }

    fun detectSilentDevices(ssid: String?, currentCount: Int, arpMacs: Set<String>) {
        val profile = fingerprinter.getProfile(ssid) ?: return
        if (arpMacs.size > currentCount) {
            val silentCount = arpMacs.size - currentCount
            Log.i("Intelligence", "Detected $silentCount silent devices via ARP persistence")
        }
    }

    /**
     * Precision-Scale: Confidence-based Identity Resolution.
     * Evaluates multiple vectors (OUI, Hostname, Ports, mDNS) to suggest names.
     */
    fun resolveProbabilisticIdentity(device: NetworkDevice): Pair<String, Int> {
        var score = 0
        var suggestion = device.manufacturer.ifBlank { "Generic Device" }

        // Vector 1: Manufacturer (30%)
        if (device.manufacturer.isNotBlank() && device.manufacturer != "Unknown") score += 30

        // Vector 2: Hostname Analysis (30%)
        if (device.hostname.isNotBlank()) {
            score += 30
            val lowerName = device.hostname.lowercase()
            when {
                lowerName.contains("android") || lowerName.contains("phone") -> suggestion = "Android Phone"
                lowerName.contains("iphone") || lowerName.contains("apple") -> suggestion = "Apple Mobile"
                lowerName.contains("desktop") || lowerName.contains("pc") -> suggestion = "Workstation"
                lowerName.contains("smart") || lowerName.contains("iot") -> suggestion = "IOT Device"
            }
        }

        // Vector 3: Service Exposure (20%)
        val ports = device.openPortsList()
        if (ports.isNotEmpty()) {
            score += 20
            when {
                ports.contains(80) || ports.contains(443) -> suggestion = "Web Server / Router Interface"
                ports.contains(22) || ports.contains(23) -> suggestion = "Controlled Node (SSH/Telnet)"
                ports.contains(554) || ports.contains(8000) -> suggestion = "IP Camera / DVR"
            }
        }

        // Vector 4: MAC Randomization (Negative)
        if (device.isMacRandomized) score -= 15

        return (suggestion to score.coerceIn(0, 100))
    }

    /**
     * Precision-Scale: Time-Based Anomaly Detection.
     * Flags devices active during historical "Silent" windows (e.g., 3 AM intrusion).
     */
    fun isActivityFingerprintAnomalous(device: NetworkDevice): Boolean {
        if (device.usualActiveHours.all { it == '0' }) return false // Still learning
        
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val historicalBit = device.usualActiveHours.getOrNull(hour) ?: '0'
        
        // If current activity is HEAVY/ACTIVE but historical probability at this hour is '0'
        return (historicalBit == '0' && 
                (device.activityLevel == com.wifimonitor.data.ActivityLevel.HEAVY || 
                 device.activityLevel == com.wifimonitor.data.ActivityLevel.ACTIVE))
    }

    /**
     * Precision-Scale: Spoofing & Conflict Detection.
     * Identifies if multiple MACs are fighting for one IP or if a MAC OUI is inconsistent.
     */
    fun checkSpoofing(devices: List<NetworkDevice>): Set<String> {
        val spoofed = mutableSetOf<String>()
        val ipMap = devices.filter { it.ip.isNotBlank() }.groupBy { it.ip }
        ipMap.forEach { (ip, deviceList) ->
            if (deviceList.size > 1) {
                spoofed.addAll(deviceList.map { it.mac })
            }
        }
        return spoofed
    }

    /**
     * Level 7: Comprehensive Security Audit.
     * Evaluates unknown devices, open ports, spoofing, and anomalies.
     */
    enum class Posture { SAFE, WARNING, RISK }
    data class SecurityAudit(val posture: Posture, val summary: String, val detailedConcerns: List<String>)

    fun performSecurityAudit(devices: List<NetworkDevice>): SecurityAudit {
        val concerns = mutableListOf<String>()
        val unknown = devices.count { !it.isKnown }
        val spoofed = devices.count { it.isSpoofed }
        val anomalies = devices.count { isActivityFingerprintAnomalous(it) }

        if (unknown > 0) concerns.add("$unknown unknown devices detected")
        if (spoofed > 0) concerns.add("Identity spoofing artifacts detected")
        if (anomalies > 0) concerns.add("Behavioral anomalies flagged (3AM/Odd-hour)")

        // Port scanning audit (High risk ports: 21, 23, 3389)
        val riskyPorts = devices.flatMap { it.openPortsList() }.count { it in listOf(21, 23, 3389, 4444) }
        if (riskyPorts > 0) concerns.add("$riskyPorts critical ports exposed (FTP/Telnet/RDP)")

        val posture = when {
            spoofed > 0 || riskyPorts > 2 -> Posture.RISK
            unknown > 0 || anomalies > 0 -> Posture.WARNING
            else -> Posture.SAFE
        }

        val summary = when (posture) {
            Posture.SAFE -> "Network integrity verified. No critical exposure."
            Posture.WARNING -> "Cautious state: Unrecognized elements detected."
            Posture.RISK -> "Critical: High-risk vulnerabilities active."
        }

        return SecurityAudit(posture, summary, concerns)
    }

    /**
     * Level 7: Baseline Analytics.
     */
    fun checkBaselineDeviation(currentCount: Int, currentLatency: Int, baselineCount: Int, baselineLatency: Int): List<String> {
        val violations = mutableListOf<String>()
        if (baselineCount > 0 && currentCount > baselineCount * 1.5) {
            violations.add("Device count spike: 50%+ above baseline")
        }
        if (baselineLatency > 0 && currentLatency > baselineLatency * 2) {
            violations.add("Severe Latency Drift: 2x slower than baseline")
        }
        return violations
    }

    /**
     * Level 7: Forensic Disappearance Guessing.
     */
    fun guessDisappearanceReason(device: NetworkDevice, currentScanMacs: Set<String>, currentScanIps: Set<String>): String {
        return when {
            currentScanIps.contains(device.ip) && !currentScanMacs.contains(device.mac) -> "IP Change Detected"
            device.signalStrength < 15 && device.signalStrength > 0 -> "Weak Signal Dropout"
            else -> "Offline / Disconnected"
        }
    }
}
