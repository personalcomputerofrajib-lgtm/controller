package com.wifimonitor.analyzer

import com.wifimonitor.data.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart Behavior Engine — converts raw network signals into human-readable insights.
 * Runs after each scan to enrich devices with behavioral labels, smart tags, and activity levels.
 */
@Singleton
class BehaviorEngine @Inject constructor() {

    fun analyzeDevices(
        devices: List<NetworkDevice>,
        trafficRecords: List<com.wifimonitor.data.TrafficRecord>
    ): List<NetworkDevice> {
        return devices.map { device -> analyzeDevice(device, trafficRecords) }
    }

    fun analyzeDevice(
        device: NetworkDevice,
        allTraffic: List<com.wifimonitor.data.TrafficRecord>
    ): NetworkDevice {
        val deviceTraffic = allTraffic.filter {
            it.deviceMac == device.mac || it.deviceMac == device.ip
        }

        val activityLevel = computeActivityLevel(device, deviceTraffic)
        val behaviorLabel = computeBehaviorLabel(activityLevel, deviceTraffic, device)
        val smartTags = computeSmartTags(device, activityLevel, deviceTraffic)
        val recentDomains = deviceTraffic
            .sortedByDescending { it.timestamp }
            .distinctBy { it.domain }
            .take(5)
            .joinToString("|") { it.domain }

        return device.copy(
            activityLevel = activityLevel,
            behaviorLabel = behaviorLabel,
            smartTags = smartTags.joinToString(","),
            recentDomains = recentDomains
        )
    }

    private fun computeActivityLevel(
        device: NetworkDevice,
        traffic: List<com.wifimonitor.data.TrafficRecord>
    ): ActivityLevel {
        if (device.status != DeviceStatus.ONLINE) return ActivityLevel.IDLE

        val now = System.currentTimeMillis()
        val last5Min = now - 5 * 60_000L
        val last1Min = now - 60_000L

        val recent5 = traffic.count { it.timestamp > last5Min }
        val recent1 = traffic.count { it.timestamp > last1Min }

        val pingMs = device.pingResponseMs

        return when {
            recent1 >= 10 || recent5 >= 40 -> ActivityLevel.HEAVY
            recent1 >= 5 || recent5 >= 20 -> ActivityLevel.ACTIVE
            recent1 >= 2 || recent5 >= 8 -> ActivityLevel.BROWSING
            recent5 >= 2 || pingMs in 1..80 -> ActivityLevel.LOW
            else -> ActivityLevel.IDLE
        }
    }

    private fun computeBehaviorLabel(
        level: ActivityLevel,
        traffic: List<com.wifimonitor.data.TrafficRecord>,
        device: NetworkDevice
    ): String {
        val domains = traffic.map { it.domain.lowercase() }
        val hasStreaming = domains.any { d ->
            listOf("youtube", "netflix", "twitch", "spotify", "apple.music",
                "primevideo", "disneyplus", "hulu", "tiktok").any { d.contains(it) }
        }
        val hasSocial = domains.any { d ->
            listOf("instagram", "facebook", "twitter", "x.com", "snapchat",
                "whatsapp", "telegram", "discord").any { d.contains(it) }
        }
        val hasBrowsing = domains.any { d ->
            listOf("google", "bing", "duckduckgo", "wikipedia", "reddit",
                "stackoverflow", "github").any { d.contains(it) }
        }
        val hasCloud = domains.any { d ->
            listOf("icloud", "dropbox", "drive.google", "onedrive",
                "amazonaws", "cloudfront").any { d.contains(it) }
        }
        val hasGaming = domains.any { d ->
            listOf("xbox", "playstation", "nintendo", "steam", "epicgames", 
                "riotgames", "roblox", "minecraft").any { d.contains(it) }
        }
        val hasSystem = domains.any { d ->
            listOf("apple-cloudkit", "android.pool.ntp.org", "windowsupdate", 
                "crashlytics", "googleapis", "gstatic").any { d.contains(it) }
        }
        val hasAds = domains.any { d ->
            listOf("doubleclick", "adservice", "googleadservices", "advertising").any { d.contains(it) }
        }
        val hasIoT = device.deviceType == DeviceType.IOT ||
            domains.any { d -> listOf("mqtt", "homekit", "smartthings", "tuya").any { d.contains(it) } }

        return when {
            hasStreaming && level == ActivityLevel.HEAVY -> "Streaming-like continuous activity"
            hasStreaming -> "Streaming pattern detected"
            hasGaming -> "Gaming traffic patterns"
            hasSocial && level.ordinal >= ActivityLevel.BROWSING.ordinal -> "Social media browsing"
            hasBrowsing -> "Web browsing pattern"
            hasCloud -> "Cloud sync activity"
            hasSystem -> "System background updates"
            hasAds -> "Advertising/Telemetry traffic"
            hasIoT -> "IoT background service"
            level == ActivityLevel.HEAVY -> "Heavy network usage"
            level == ActivityLevel.ACTIVE -> "Continuous usage"
            level == ActivityLevel.BROWSING -> "Browsing-like activity"
            level == ActivityLevel.LOW -> "Low background usage"
            else -> "Idle / no activity"
        }
    }

    private fun computeSmartTags(
        device: NetworkDevice,
        level: ActivityLevel,
        traffic: List<com.wifimonitor.data.TrafficRecord>
    ): List<String> {
        val tags = mutableListOf<String>()

        // Primary device — seen very frequently across many sessions
        if (device.totalSessions >= 5 && device.averageSessionMs > 30 * 60_000L) {
            tags.add("Primary device")
        }

        // Always online — very long session or always present
        val sessionHours = device.sessionDuration / 3_600_000L
        if (device.status == DeviceStatus.ONLINE && sessionHours >= 8) {
            tags.add("Always online")
        }

        // Heavy user
        if (level == ActivityLevel.HEAVY || level == ActivityLevel.ACTIVE) {
            tags.add("Heavy user")
        }

        // Streaming capable
        val domains = traffic.map { it.domain.lowercase() }
        val isStreamingCapable = domains.any { d ->
            listOf("youtube", "netflix", "spotify", "twitch", "primevideo").any { d.contains(it) }
        }
        if (isStreamingCapable) tags.add("Streaming capable")

        // Background service
        if (device.deviceType == DeviceType.IOT) tags.add("Background service")

        // New device
        val ageMs = System.currentTimeMillis() - device.firstSeen
        if (ageMs < 24 * 60 * 60 * 1000L) tags.add("New device")

        // Frequently active
        if (device.totalSessions >= 10) tags.add("Frequently active")

        return tags
    }

    /**
     * Compute network health score (0-100).
     * Factors: unknown devices, blocked devices, heavy activity, alert count
     */
    fun computeHealthScore(
        devices: List<NetworkDevice>,
        alertCount: Int
    ): Int {
        var score = 100

        val unknownCount = devices.count { it.deviceGroup == DeviceGroup.UNKNOWN && it.status == DeviceStatus.ONLINE }
        val heavyCount = devices.count { it.activityLevel == ActivityLevel.HEAVY }
        val blockedAttempts = devices.count { it.isBlocked && it.status == DeviceStatus.ONLINE }
        val totalOnline = devices.count { it.status == DeviceStatus.ONLINE }

        // Unknown devices penalty
        score -= unknownCount * 8

        // Heavy activity penalty (might indicate unusual traffic)
        if (heavyCount > totalOnline / 2) score -= 10

        // Blocked device attempting to connect
        score -= blockedAttempts * 15

        // Too many alerts recently
        score -= minOf(alertCount * 3, 20)

        // Many devices on network — slight caution
        if (totalOnline > 15) score -= 5

        return score.coerceIn(0, 100)
    }

    fun healthLabel(score: Int): String = when {
        score >= 90 -> "Excellent"
        score >= 75 -> "Good"
        score >= 60 -> "Fair"
        score >= 40 -> "Poor"
        else -> "Critical"
    }

    fun identifyPersonality(device: NetworkDevice, history: List<DeviceHistory>): String {
        val volumeGb = (device.totalDownloadBytes + device.totalUploadBytes) / (1024f * 1024f * 1024f)
        val stability = device.reliabilityPct
        
        return when {
            volumeGb > 10.0 -> "The Data Miner"
            volumeGb > 2.0 -> "The Steady Streamer"
            stability < 50 -> "Unstable Link"
            device.totalSessions > 50 -> "The Frequent Guest"
            else -> "Predictable Device"
        }
    }
}
