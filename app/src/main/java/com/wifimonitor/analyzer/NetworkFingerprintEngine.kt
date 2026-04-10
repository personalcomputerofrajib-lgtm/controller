package com.wifimonitor.analyzer

import android.util.Log
import com.wifimonitor.data.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 6 Engineering Component: Network Fingerprinting.
 * Establishes a behavioral baseline for the ENTIRE network to detect
 * structural changes or massive load anomalies.
 */
@Singleton
class NetworkFingerprintEngine @Inject constructor() {

    data class NetworkProfile(
        val ssid: String?,
        var typicalDeviceCount: Int = 0,
        var typicalActivityScore: Float = 0f,
        var mostActiveHour: Int = -1,
        var fingerprintHash: Int = 0,
        var lastUpdated: Long = 0L
    )

    private val profiles = mutableMapOf<String, NetworkProfile>()

    /**
     * Updates the fingerprint based on current online devices.
     */
    fun updateFingerprint(ssid: String?, onlineDevices: List<NetworkDevice>) {
        val key = ssid ?: "unknown_network"
        val profile = profiles.getOrPut(key) { NetworkProfile(ssid) }

        val currentCount = onlineDevices.size
        val currentActivity = onlineDevices.sumOf { 
            when (it.activityLevel) {
                com.wifimonitor.data.ActivityLevel.HEAVY -> 1.0
                com.wifimonitor.data.ActivityLevel.ACTIVE -> 0.7
                else -> 0.1
            }
        }.toFloat() / currentCount.coerceAtLeast(1)

        // Exponential moving average for baseline
        profile.typicalDeviceCount = ((profile.typicalDeviceCount * 0.9) + (currentCount * 0.1)).toInt()
        profile.typicalActivityScore = (profile.typicalActivityScore * 0.9f) + (currentActivity * 0.1f)
        
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (currentActivity > 0.5f) {
            profile.mostActiveHour = hour
        }

        profile.lastUpdated = System.currentTimeMillis()
        
        // Generate a simple hash based on MAC addresses to detect structural changes
        profile.fingerprintHash = onlineDevices.map { it.mac }.sorted().hashCode()
    }

    /**
     * Checks if current network state is "Out of Baseline".
     */
    fun checkStructuralAnomaly(ssid: String?, currentCount: Int): Boolean {
        val profile = profiles[ssid ?: "unknown_network"] ?: return false
        if (profile.typicalDeviceCount == 0) return false

        val diff = kotlin.math.abs(currentCount - profile.typicalDeviceCount)
        return diff > (profile.typicalDeviceCount * 0.5).coerceAtLeast(3.0)
    }

    fun getProfile(ssid: String?): NetworkProfile? = profiles[ssid ?: "unknown_network"]
}
