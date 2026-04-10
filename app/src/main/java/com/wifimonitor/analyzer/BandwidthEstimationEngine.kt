package com.wifimonitor.analyzer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 6 Engineering Component: Bandwidth Estimation.
 * Estimates per-device throughput when direct router stats are unavailable.
 */
@Singleton
class BandwidthEstimationEngine @Inject constructor() {

    data class BandwidthEstimate(
        val downloadMbps: Float,
        val uploadMbps: Float,
        val congestionIndex: Float // 0 to 1
    )

    /**
     * Estimates bandwidth using heuristic activity density and latency jitter.
     */
    fun estimate(
        recentBytes: Long,
        timeDeltaMs: Long,
        latencyJitterMs: Float
    ): BandwidthEstimate {
        if (timeDeltaMs <= 0) return BandwidthEstimate(0f, 0f, 0f)

        // Basic throughput calculation
        val mbps = (recentBytes * 8f) / (timeDeltaMs / 1000f) / 1_000_000f

        // Congestion index: higher jitter under load indicates a bottleneck
        val congestion = (latencyJitterMs / 50f).coerceIn(0f, 1f)

        // For non-gateway mode, we split bytes 80/20 as a heuristic for DL/UL
        return BandwidthEstimate(
            downloadMbps = mbps * 0.8f,
            uploadMbps = mbps * 0.2f,
            congestionIndex = congestion
        )
    }
}
