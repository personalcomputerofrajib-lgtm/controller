package com.wifimonitor.analyzer

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local intelligence engine — learns device patterns over time to:
 * - Distinguish normal vs abnormal behavior
 * - Reduce false alerts (noise suppression)
 * - Identify typical activity windows
 * - Build per-device baselines
 */
@Singleton
class PatternLearner @Inject constructor() {

    data class DeviceBaseline(
        val mac: String,
        var typicalActivityScore: Float = 0.5f,  // learned mean
        var activityStdDev: Float = 0.1f,        // learned deviation
        var typicalLatencyMs: Int = 30,
        var latencyStdDev: Float = 10f,
        var onlineHoursMask: Int = 0,            // Bitmask (1 << hour)
        var observationCount: Int = 0,
        var lastUpdated: Long = 0L
    )

    private val baselines = ConcurrentHashMap<String, DeviceBaseline>()

    companion object {
        const val LEARNING_RATE = 0.05f  // slow adaptation
        const val MIN_OBSERVATIONS = 10  // before baseline is trusted
        const val MAX_BASELINES = 150   // Prevent unbounded memory growth (Killer #17)
        const val MIN_ACTIVITY_STD_DEV = 0.05f // Audit 9: Noise Floor
        const val MIN_LATENCY_STD_DEV = 5.0f
    }

    /** Update baseline with new observation */
    fun observe(mac: String, activityScore: Float, latencyMs: Int) {
        if (baselines.size > MAX_BASELINES && !baselines.containsKey(mac)) {
            // Prune oldest if full
            baselines.entries.minByOrNull { it.value.lastUpdated }?.let { baselines.remove(it.key) }
        }

        val baseline = baselines.getOrPut(mac) { DeviceBaseline(mac) }
        synchronized(baseline) {
            baseline.observationCount++
            baseline.lastUpdated = System.currentTimeMillis()

            // Exponential moving average for activity
            baseline.typicalActivityScore = lerp(baseline.typicalActivityScore, activityScore, LEARNING_RATE)

            // Update activity std dev (Audit 9: With Noise Floor)
            val actDiff = kotlin.math.abs(activityScore - baseline.typicalActivityScore)
            baseline.activityStdDev = lerp(baseline.activityStdDev, actDiff, LEARNING_RATE * 0.5f)
                .coerceAtLeast(MIN_ACTIVITY_STD_DEV)

            // Latency baseline
            if (latencyMs > 0) {
                baseline.typicalLatencyMs = lerp(
                    baseline.typicalLatencyMs.toFloat(),
                    latencyMs.toFloat(),
                    LEARNING_RATE
                ).toInt()
                val latDiff = kotlin.math.abs(latencyMs - baseline.typicalLatencyMs).toFloat()
                baseline.latencyStdDev = lerp(baseline.latencyStdDev, latDiff, LEARNING_RATE * 0.5f)
                    .coerceAtLeast(MIN_LATENCY_STD_DEV)
            }

            // Track online hours (Audit 9: Using Bitmask)
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            baseline.onlineHoursMask = baseline.onlineHoursMask or (1 shl hour)
        }
    }

    /** Is current behavior anomalous? */
    fun isAnomalous(mac: String, activityScore: Float, latencyMs: Int): AnomalyResult {
        val baseline = baselines[mac] ?: return AnomalyResult(false, "Insufficient data")
        if (baseline.observationCount < MIN_OBSERVATIONS) return AnomalyResult(false, "Learning…")

        val reasons = mutableListOf<String>()

        // Activity spike?
        val activityDelta = activityScore - baseline.typicalActivityScore
        if (activityDelta > baseline.activityStdDev * 2.5f) {
            reasons.add("Activity spike: %.1f%% above normal".format(activityDelta * 100))
        }

        // Latency anomaly?
        if (latencyMs > 0) {
            val latDelta = latencyMs - baseline.typicalLatencyMs
            if (latDelta > baseline.latencyStdDev * 3f) {
                reasons.add("Latency spike: ${latDelta}ms above normal")
            }
        }

        // Unusual hour?
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val seenThisHour = (baseline.onlineHoursMask and (1 shl hour)) != 0
        if (!seenThisHour && baseline.observationCount > 50) {
            reasons.add("Unusual online hour: $hour:00")
        }

        return AnomalyResult(reasons.isNotEmpty(), reasons.joinToString("; "))
    }

    /** Get learned baseline for display */
    fun getBaseline(mac: String): DeviceBaseline? = baselines[mac]

    /** Is baseline mature enough to trust? */
    fun isBaselineMature(mac: String): Boolean =
        (baselines[mac]?.observationCount ?: 0) >= MIN_OBSERVATIONS

    fun pruneStale() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        baselines.entries.removeIf { it.value.lastUpdated < cutoff }
    }

    data class AnomalyResult(val isAnomalous: Boolean, val reason: String)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
