package com.wifimonitor.analyzer

import com.wifimonitor.data.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReputationEngine @Inject constructor(
    private val sessionTracker: SessionTracker
) {
    
    data class ReputationResult(
        val score: Int, // 0-100
        val label: String,
        val traits: List<String>
    )

    fun calculateReputation(device: NetworkDevice): ReputationResult {
        val traits = mutableListOf<String>()
        var score = 70 // Base reputation

        val session = sessionTracker.getSession(device.mac)
        
        // 1. Stability Trait
        if (session != null && session.stabilityScore > 0.9f) {
            score += 15
            traits.add("Highly Stable")
        } else if (session != null && session.stabilityScore < 0.4f) {
            score -= 20
            traits.add("Volatile Link")
        }

        // 2. Consistency Trait
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (device.totalSessions > 100) {
            score += 10
            traits.add("Reliable Asset")
        }

        // 3. Security Profile (Placeholder)
        if (device.isMacRandomized) {
            score -= 5
            traits.add("Private Identity")
        }

        val finalScore = score.coerceIn(0, 100)
        val label = when {
            finalScore > 85 -> "Gold Standard"
            finalScore > 70 -> "Reliable"
            finalScore > 40 -> "Standard"
            else -> "Experimental/Unstable"
        }

        return ReputationResult(finalScore, label, traits)
    }
}
