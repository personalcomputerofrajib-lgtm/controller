package com.wifimonitor.analyzer

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Production signal processing with:
 * - Precision latency profiling (min/max/median/jitter)
 * - Packet loss & reliability tracking
 * - EMA-smoothed activity
 * - Sparkline-ready data windows
 */
@Singleton
class SignalProcessor @Inject constructor() {

    private val latencyWindows = ConcurrentHashMap<String, ArrayDeque<Int>>()
    private val activityWindows = ConcurrentHashMap<String, ArrayDeque<Float>>()
    private val probeResults = ConcurrentHashMap<String, ArrayDeque<Boolean>>()
    private val lastLatency = ConcurrentHashMap<String, Int>()
    private val jitterCache = ConcurrentHashMap<String, Double>()

    companion object {
        const val WINDOW_SIZE = 30
        const val MIN_SAMPLES = 3
    }

    // ── Latency recording ──

    fun recordLatency(mac: String, latencyMs: Int) {
        val window = latencyWindows.getOrPut(mac) { ArrayDeque(WINDOW_SIZE + 2) }
        synchronized(window) {
            window.addLast(latencyMs)
            if (window.size > WINDOW_SIZE) window.removeFirst()
        }
        
        // ── RFC 3550 Jitter Calculation ──
        val prev = lastLatency[mac]
        if (prev != null) {
            val d = Math.abs(latencyMs - prev)
            val currentJitter = jitterCache[mac] ?: 0.0
            // J = J + (|D| - J)/16
            val nextJitter = currentJitter + (d - currentJitter) / 16.0
            jitterCache[mac] = nextJitter
        }
        lastLatency[mac] = latencyMs
    }

    fun smoothedLatency(mac: String): Int {
        val window = latencyWindows[mac] ?: return -1
        synchronized(window) {
            if (window.size < MIN_SAMPLES) return window.lastOrNull() ?: -1
            var ema = window.first().toDouble()
            val alpha = 0.3
            for (v in window) { ema = alpha * v + (1 - alpha) * ema }
            return ema.toInt()
        }
    }

    fun latencyHistory(mac: String): List<Int> {
        val window = latencyWindows[mac] ?: return emptyList()
        synchronized(window) { return window.toList() }
    }

    // ── Precision latency stats ──

    data class LatencyStats(
        val min: Int,
        val max: Int,
        val median: Int,
        val mean: Int,
        val jitter: Int,     // standard deviation
        val samples: Int
    )

    fun getLatencyStats(mac: String): LatencyStats? {
        val window = latencyWindows[mac] ?: return null
        synchronized(window) {
            if (window.size < MIN_SAMPLES) return null
            val sorted = window.sorted()
            val min = sorted.first()
            val max = sorted.last()
            val median = sorted[sorted.size / 2]
            val mean = sorted.average().toInt()

            // RFC 3550 Jitter
            val jitter = (jitterCache[mac] ?: 0.0).toInt()

            return LatencyStats(min, max, median, mean, jitter, sorted.size)
        }
    }

    // ── Activity score ──

    fun recordActivity(mac: String, score: Float) {
        val window = activityWindows.getOrPut(mac) { ArrayDeque(WINDOW_SIZE + 2) }
        synchronized(window) {
            window.addLast(score.coerceIn(0f, 1f))
            if (window.size > WINDOW_SIZE) window.removeFirst()
        }
    }

    fun smoothedActivity(mac: String): Float {
        val window = activityWindows[mac] ?: return 0f
        synchronized(window) {
            if (window.isEmpty()) return 0f
            var ema = window.first().toDouble()
            val alpha = 0.25
            for (v in window) { ema = alpha * v + (1 - alpha) * ema }
            return ema.toFloat()
        }
    }

    fun activityHistory(mac: String): List<Float> {
        val window = activityWindows[mac] ?: return emptyList()
        synchronized(window) { return window.toList() }
    }

    // ── Probe reliability (packet loss) ──

    fun recordProbe(mac: String, success: Boolean) {
        val window = probeResults.getOrPut(mac) { ArrayDeque(WINDOW_SIZE + 2) }
        synchronized(window) {
            window.addLast(success)
            if (window.size > WINDOW_SIZE) window.removeFirst()
        }
    }

    data class ReliabilityStats(
        val successCount: Int,
        val failCount: Int,
        val reliabilityPct: Int,
        val totalProbes: Int
    )

    fun getReliability(mac: String): ReliabilityStats {
        val window = probeResults[mac] ?: return ReliabilityStats(0, 0, 0, 0)
        synchronized(window) {
            val total = window.size
            if (total == 0) return ReliabilityStats(0, 0, 0, 0)
            val successes = window.count { it }
            val fails = total - successes
            val pct = ((successes.toFloat() / total) * 100).toInt()
            return ReliabilityStats(successes, fails, pct, total)
        }
    }

    /** Stability as percentage (backward compat) */
    fun stability(mac: String): Int = getReliability(mac).reliabilityPct

    fun clearDevice(mac: String) {
        latencyWindows.remove(mac)
        activityWindows.remove(mac)
        probeResults.remove(mac)
    }

    /**
     * Estimates distance in meters based on RSSI using Log-Distance Path Loss model.
     * Formula: d = 10 ^ ((L0 - RSSI) / (10 * n))
     * L0 = signal strength at 1m (baseline)
     * n = path loss exponent (2.0 for free space, 3.0-4.0 for indoor)
     */
    fun calculateEstimatedDistance(rssi: Int): Double {
        if (rssi >= 0 || rssi < -100) return -1.0
        val baselineRssi = -35.0 // Calibrated for typical Android antennas at 1m
        val pathLossExponent = 3.2 // Average indoor value
        
        return Math.pow(10.0, (baselineRssi - rssi) / (10 * pathLossExponent))
    }
}
