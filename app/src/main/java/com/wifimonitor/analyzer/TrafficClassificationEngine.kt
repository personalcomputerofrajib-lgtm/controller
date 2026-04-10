package com.wifimonitor.analyzer

import com.wifimonitor.data.ActivityLevel
import javax.inject.Inject
import javax.inject.Singleton

enum class TrafficType {
    STREAMING, REALTIME, BACKGROUND, BULK, SYSTEM, UNKNOWN
}

/**
 * Level 6 Engineering Component: Heuristic Traffic Classification.
 * Classifies traffic without payload inspection based on session behavior.
 */
@Singleton
class TrafficClassificationEngine @Inject constructor() {

    data class TrafficSignature(
        val type: TrafficType,
        val confidence: Float,
        val description: String
    )

    /**
     * Heuristically classifies traffic based on metadata patterns.
     */
    fun classify(
        mac: String,
        burstiness: Float, // 0 (constant) to 1 (highly bursty)
        persistenceSec: Long,
        volumeBytes: Long
    ): TrafficSignature {
        return when {
            // High volume, low burstiness, long duration = Streaming
            volumeBytes > 10 * 1024 * 1024 && burstiness < 0.3f && persistenceSec > 60 -> 
                TrafficSignature(TrafficType.STREAMING, 0.85f, "High-bandwidth continuous stream (Video/Audio)")

            // Low volume, high frequency, long duration = Real-time/Gaming
            volumeBytes < 1 * 1024 * 1024 && persistenceSec > 300 && burstiness < 0.5f -> 
                TrafficSignature(TrafficType.REALTIME, 0.7f, "Low-latency persistent session (Gaming/VoIP)")

            // High volume, short duration = Bulk Transfer
            volumeBytes > 50 * 1024 * 1024 && persistenceSec < 120 -> 
                TrafficSignature(TrafficType.BULK, 0.9f, "High-speed file transfer / Download")

            // Intermittent, low volume = Background
            volumeBytes < 100 * 1024 && burstiness > 0.7f -> 
                TrafficSignature(TrafficType.BACKGROUND, 0.6f, "Intermittent background sync / Heartbeats")

            else -> TrafficSignature(TrafficType.UNKNOWN, 0.3f, "Variable network traffic")
        }
    }
}
