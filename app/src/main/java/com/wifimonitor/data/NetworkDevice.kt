package com.wifimonitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DeviceStatus { ONLINE, OFFLINE, UNKNOWN }
enum class DeviceType { PHONE, LAPTOP, TV, ROUTER, IOT, TABLET, UNKNOWN }
enum class DeviceGroup { MY_DEVICES, FAMILY, UNKNOWN, IOT }
enum class TrustLevel { TRUSTED, OBSERVED, UNKNOWN }

enum class ActivityLevel(val label: String, val emoji: String) {
    IDLE("Idle", "💤"),
    LOW("Low activity", "🟢"),
    BROWSING("Browsing-like", "🌐"),
    ACTIVE("Continuous usage", "🟡"),
    HEAVY("Heavy activity", "🔴")
}

@Entity(tableName = "devices")
data class NetworkDevice(
    @PrimaryKey
    val mac: String,
    val ip: String = "",
    val hostname: String = "",
    val manufacturer: String = "",
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val deviceGroup: DeviceGroup = DeviceGroup.UNKNOWN,
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN,
    val status: DeviceStatus = DeviceStatus.UNKNOWN,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val isKnown: Boolean = false,
    val nickname: String = "",
    val openPorts: String = "",
    val signalStrength: Int = -1,
    val isBlocked: Boolean = false,
    val isTracked: Boolean = false,

    // Activity & Behavior
    val activityLevel: ActivityLevel = ActivityLevel.IDLE,
    val behaviorLabel: String = "",
    val sessionDuration: Long = 0L,
    val totalSessions: Int = 0,
    val averageSessionMs: Long = 0L,
    val pingResponseMs: Int = -1,

    // Precision latency
    val latencyMin: Int = -1,
    val latencyMax: Int = -1,
    val latencyMedian: Int = -1,
    val jitterMs: Int = -1,

    // Reliability
    val reliabilityPct: Int = 0,       // 0-100% probe success
    val probeSuccessCount: Int = 0,
    val probeFailCount: Int = 0,

    // Identity
    val fingerprintHash: String = "",   // stable device ID across IP changes
    val isMacRandomized: Boolean = false,

    // Smart tags (comma-separated)
    val smartTags: String = "",

    // Last known DNS domains (pipe-separated, top 5)
    val recentDomains: String = "",

    // Network interface where device was detected
    val networkInterface: String = "",

    // Bandwidth Auditing
    val totalDownloadBytes: Long = 0L,
    val totalUploadBytes: Long = 0L,

    // --- Precision-Scale Add-Ons ---
    val networkProfileId: String = "default", // Partition devices by SSID/BSSID
    val usualActiveHours: String = "0".repeat(24), // 24-bit pattern of usual activity
    val confidenceScore: Int = 0,
    val suggestedName: String = "",
    val lastSeenPorts: String = "",
    val isSpoofed: Boolean = false,
    val disappearanceReason: String? = null,
    val baselineLatency: Int = -1,
    
    // Live Traffic (Final Power Tier)
    val downloadRateMbps: Float = 0f,
    val uploadRateMbps: Float = 0f,
    val currentAppType: String = ""
) {
    val displayName: String
        get() = nickname.ifBlank { hostname.ifBlank { "Unknown (${mac.takeLast(5)})" } }

    val shortMac: String
        get() = mac.takeLast(8).uppercase()

    fun openPortsList(): List<Int> =
        openPorts.split(",").mapNotNull { it.trim().toIntOrNull() }

    fun smartTagsList(): List<String> =
        if (smartTags.isBlank()) emptyList()
        else smartTags.split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun recentDomainsList(): List<String> =
        if (recentDomains.isBlank()) emptyList()
        else recentDomains.split("|").map { it.trim() }.filter { it.isNotBlank() }

    val trustIcon: String get() = when (trustLevel) {
        TrustLevel.TRUSTED -> "🛡️"
        TrustLevel.OBSERVED -> "👁️"
        TrustLevel.UNKNOWN -> "❓"
    }

    val reliabilityLabel: String get() = when {
        reliabilityPct >= 95 -> "Excellent"
        reliabilityPct >= 80 -> "Stable"
        reliabilityPct >= 60 -> "Fair"
        reliabilityPct >= 40 -> "Unstable"
        else -> "Poor"
    }

    val connectionQuality: String get() = when {
        jitterMs < 0 -> "—"
        jitterMs < 5 -> "Rock solid"
        jitterMs < 15 -> "Stable connection"
        jitterMs < 40 -> "Moderate fluctuation"
        else -> "Unstable / fluctuating"
    }
}
