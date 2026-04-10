package com.wifimonitor.analyzer

import com.wifimonitor.data.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 7 Congestion Analysis Component.
 * Correlates device activity with network-wide health to find the "Bottleneck Source."
 */
@Singleton
class CongestionSourceDetector @Inject constructor(
    private val intelligenceEngine: IntelligenceEngine
) {
    
    data class CongestionSource(
        val mac: String,
        val contributionPct: Int, // 0-100
        val reasoning: String
    )

    /**
     * Identifies the primary source of network congestion.
     */
    fun findCongestionSource(devices: List<NetworkDevice>): CongestionSource? {
        val pressure = intelligenceEngine.computeNetworkPressure(devices)
        if (pressure.score < 40) return null // No significant congestion

        val online = devices.filter { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }
        
        // Find the device with the highest activity score and highest latency jitter
        val culprit = online.maxByOrNull { device ->
            val actVal = when(device.activityLevel) {
                com.wifimonitor.data.ActivityLevel.HEAVY -> 100
                com.wifimonitor.data.ActivityLevel.ACTIVE -> 70
                else -> 10
            }
            actVal + device.jitterMs.toInt()
        } ?: return null

        val contribution = (CULPRIT_WEIGHT * (culprit.pingResponseMs.toFloat() / pressure.score)).coerceAtMost(100f).toInt()
        
        return if (contribution > 30) {
            CongestionSource(
                culprit.mac, 
                contribution, 
                "${culprit.displayName} is consuming high bandwidth while exhibiting latent jitter."
            )
        } else null
    }

    companion object {
        const val CULPRIT_WEIGHT = 20.0f
    }
}
