package com.wifimonitor.analyzer

import com.wifimonitor.data.ActivityLevel
import com.wifimonitor.data.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 12 Cognitive Component.
 * Translates raw metrics (Jitter, Activity, Pressure) into
 * Human-readable Narrative Insights.
 */
@Singleton
class InferenceEngine @Inject constructor(
    private val repository: com.wifimonitor.data.DeviceRepository? = null // Optional if called from VM
) {

    enum class SecurityGrade(val score: Int, val label: String, val color: String) {
        A(90, "Excellent", "#00E676"),
        B(75, "Good", "#00B0FF"),
        C(60, "Fair", "#FFD600"),
        D(40, "Risky", "#FF9100"),
        F(0, "CRITICAL", "#FF1744")
    }

    data class NarrativeReport(
        val title: String,
        val summary: String,
        val impact: String,
        val suggestion: String
    )

    fun generateHealthReport(devices: List<NetworkDevice>, pressureScore: Int): NarrativeReport {
        val online = devices.filter { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }
        
        if (online.isEmpty()) {
            return NarrativeReport(
                "Network Silent",
                "No active devices detected at this moment.",
                "Zero load",
                "Scan to discover physical presence."
            )
        }

        val highJitterDevice = online.maxByOrNull { it.jitterMs }
        val heavyLoadDevice = online.maxByOrNull { it.activityLevel.ordinal }
        
        return when {
            pressureScore > 80 -> NarrativeReport(
                "Extreme Congestion",
                "Multiple devices are saturating the network bandwidth simultaneously.",
                "High latency for all clients",
                "Consider pausing ${heavyLoadDevice?.displayName ?: "heavy users"} to restore stability."
            )
            pressureScore > 50 && (highJitterDevice?.jitterMs ?: 0) > 40 -> NarrativeReport(
                "Stability Warning",
                "${highJitterDevice?.displayName} is experiencing severe connection instability.",
                "Potential RF interference or peak load stress",
                "Check device distance from router or move to 5GHz band."
            )
            heavyLoadDevice?.activityLevel == ActivityLevel.HEAVY -> NarrativeReport(
                "Active Stream Detected",
                "${heavyLoadDevice.displayName} is consuming significant resources.",
                "Sustained throughput identified",
                "Network is healthy but working in high-load mode."
            )
            else -> NarrativeReport(
                "Optimal Health",
                "Your network is balanced with no immediate stressors.",
                "Low jitter and distributed load",
                "All systems nominal. Forensic monitoring is active."
            )
        }
    }

    /**
     * Point #13: Activity Interpretation (Continuous, Burst, Idle)
     */
    fun resolveUsagePattern(mac: String, historyCount: Int, currentActivity: ActivityLevel): String {
        return when {
            historyCount > 50 && currentActivity.ordinal >= ActivityLevel.ACTIVE.ordinal -> "Continuous Usage Pattern"
            historyCount > 20 && currentActivity == ActivityLevel.BROWSING -> "Bursty Web Activity"
            currentActivity == ActivityLevel.IDLE -> "Standby / Background Sync"
            else -> "Learning usage pattern..."
        }
    }

    /**
     * Level 20: Forensic Security Auditor
     * Calculates a 1-100 score based on hidden cameras, unknown devices, 
     * signal instability, and ISP health.
     */
    fun calculateSecurityScore(devices: List<NetworkDevice>, alerts: List<com.wifimonitor.data.AlertRecord>): Int {
        var score = 100
        
        // 1. Unknown Devices
        val unknown = devices.count { it.trustLevel == com.wifimonitor.data.TrustLevel.UNKNOWN }
        score -= (unknown * 5).coerceAtMost(30)
        
        // 2. Critical Security Alerts
        val criticalAlerts = alerts.count { it.type == com.wifimonitor.data.AlertType.HIDDEN_CAMERA_FOUND }
        if (criticalAlerts > 0) score -= 60
        
        // 3. Network Stability
        val highJitterCount = devices.count { it.jitterMs > 50 }
        score -= (highJitterCount * 3).coerceAtMost(15)
        
        // 4. Open Ports Risk
        val dangerousPorts = listOf(21, 23, 445, 3389)
        val riskyDevices = devices.count { d -> d.openPortsList().any { it in dangerousPorts } }
        score -= (riskyDevices * 10).coerceAtMost(25)
        
        return score.coerceIn(0, 100)
    }

    fun getGrade(score: Int): SecurityGrade {
        return when {
            score >= 90 -> SecurityGrade.A
            score >= 75 -> SecurityGrade.B
            score >= 60 -> SecurityGrade.C
            score >= 40 -> SecurityGrade.D
            else -> SecurityGrade.F
        }
    }

    fun generateForensicAudit(devices: List<NetworkDevice>, alerts: List<com.wifimonitor.data.AlertRecord>, pressure: Int): String {
        val score = calculateSecurityScore(devices, alerts)
        val grade = getGrade(score)
        val online = devices.count { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }
        val unknown = devices.count { it.trustLevel == com.wifimonitor.data.TrustLevel.UNKNOWN }
        
        return """
            --- FORENSIC SECURITY AUDIT ---
            Audit Score: ${score}/100 (${grade.label})
            
            SUMMARY:
            The network is currently managing $online active devices. Of these, $unknown are classified as 'Unknown' and require manual verification. 
            
            SECURITY POSTURE:
            ${if (score > 85) "Your digital perimeter is well-defended." else "Several vulnerabilities were identified during the forensic scan."}
            ${if (alerts.any { it.type == com.wifimonitor.data.AlertType.HIDDEN_CAMERA_FOUND }) "ALERT: Potential hidden monitoring hardware (RTSP/ONVIF) detected!" else "No covert surveillance hardware detected."}
            
            NETWORK HEALTH:
            Current Network Pressure is at ${pressure}%. ${if (pressure > 30) "Minor congestion identified." else "Bandwidth overhead is optimal."}
            
            RECOMMENDATION:
            ${if (unknown > 0) "- Label the $unknown unknown devices to improve baseline metrics.\n" else ""}
            ${if (score < 80) "- Perform a deep port scan on risky devices to identify open backdoors.\n" else "- Maintain current security posture. Regular background audits are active."}
        """.trimIndent()
    }
}
