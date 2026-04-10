package com.wifimonitor.analyzer

import com.wifimonitor.data.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 5 Scoring engine.
 * Computes multi-dimensional reliability and usage metrics for each device.
 */
@Singleton
class DeviceScoreEngine @Inject constructor(
    private val sessionTracker: SessionTracker
) {

    data class DeviceRatings(
        val activity: Int,
        val stability: Int,
        val presence: Int,
        val overall: Int
    )

    fun computeRatings(device: NetworkDevice): DeviceRatings {
        val session = sessionTracker.getSession(device.mac)
        
        // 1. Stability Rating (0-100)
        // Based on smoothed latency variance from SessionTracker
        val stability = (session?.stabilityScore ?: 1.0f) * 100

        // 2. Presence Rating (0-100)
        // online time / total time since first seen
        val totalTime = System.currentTimeMillis() - device.firstSeen
        val presence = if (totalTime > 0) {
            (device.sessionDuration.toFloat() / totalTime.toFloat() * 100).coerceIn(0f, 100f)
        } else 0f

        // 3. Activity Rating (0-100)
        // based on peak activity and average session length
        val sessionBonus = (device.totalSessions.coerceAtMost(20) * 5)
        val activity = ( (session?.peakActivityScore ?: 0f) * 50 + sessionBonus ).coerceIn(0f, 100f)

        val overall = (stability * 0.4 + presence * 0.3 + activity * 0.3).toInt()

        return DeviceRatings(
            activity = activity.toInt(),
            stability = stability.toInt(),
            presence = presence.toInt(),
            overall = overall
        )
    }
}
