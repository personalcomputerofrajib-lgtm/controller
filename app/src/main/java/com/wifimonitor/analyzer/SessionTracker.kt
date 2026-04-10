package com.wifimonitor.analyzer

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-device session tracking — knows when each device's current session started,
 * accumulates duration, counts sessions, and tracks peaks.
 */
@Singleton
class SessionTracker @Inject constructor() {

    data class SessionState(
        val mac: String,
        var sessionStart: Long = 0L,
        var currentDuration: Long = 0L,       // ms since session start
        var totalSessions: Int = 0,
        var totalConnectedMs: Long = 0L,       // lifetime connected time
        var peakActivityScore: Float = 0f,
        var isActive: Boolean = false,
        var lastUpdated: Long = 0L,
        var rxBytes: Long = 0L,
        var txBytes: Long = 0L,
        val domainsCaptured: MutableSet<String> = mutableSetOf(),
        
        // ── Level 5: Behavioral High-Res Metrics ──
        val latencyHistory: MutableList<Int> = mutableListOf(),
        val activityHistory: MutableList<Float> = mutableListOf(),
        var stabilityScore: Float = 1.0f,
        var connectionCount: Int = 0,
        val portHistory: MutableList<String> = mutableListOf(), // e.g. "80:OPEN", "80:CLOSED"
        var wakeCount: Int = 0,
        var sleepCount: Int = 0
    ) {
        companion object {
            const val MAX_HISTORY = 50
        }
        val averageSessionMs: Long
            get() = if (totalSessions > 0) totalConnectedMs / totalSessions else 0L
    }

    private val sessions = ConcurrentHashMap<String, SessionState>()

    /** Called when device is found online in a scan */
    fun markOnline(mac: String) {
        val now = System.currentTimeMillis()
        val state = sessions.getOrPut(mac) { SessionState(mac) }
        synchronized(state) {
            if (!state.isActive) {
                // New session starts
                state.isActive = true
                state.sessionStart = now
                state.currentDuration = 0
                state.totalSessions++
                Log.d("SessionTracker", "Session ${state.totalSessions} started for $mac")
            } else {
                // Ongoing session — update duration
                state.currentDuration = now - state.sessionStart
            }
            state.lastUpdated = now
        }
    }

    /** Called when device goes offline or is not found in scan */
    fun markOffline(mac: String) {
        val state = sessions[mac] ?: return
        synchronized(state) {
            if (state.isActive) {
                state.isActive = false
                val sessionLen = System.currentTimeMillis() - state.sessionStart
                state.totalConnectedMs += sessionLen
                state.currentDuration = 0
                Log.d("SessionTracker", "Session ended for $mac, lasted ${sessionLen / 1000}s")
            }
        }
    }

    /** Update peak activity if current score exceeds */
    fun updatePeakActivity(mac: String, activityScore: Float) {
        val state = sessions[mac] ?: return
        synchronized(state) {
            if (activityScore > state.peakActivityScore) {
                state.peakActivityScore = activityScore
            }
        }
    }

    fun getSession(mac: String): SessionState? = sessions[mac]

    fun getSessionDuration(mac: String): Long {
        val state = sessions[mac] ?: return 0
        return if (state.isActive) System.currentTimeMillis() - state.sessionStart
        else state.currentDuration
    }

    fun getTotalSessions(mac: String): Int = sessions[mac]?.totalSessions ?: 0

    fun getAverageSession(mac: String): Long = sessions[mac]?.averageSessionMs ?: 0L

    /** Cleanup sessions for devices not seen in 24 hours */
    fun markDomain(mac: String, domain: String) {
        val state = sessions.getOrPut(mac) { SessionState(mac) }
        synchronized(state) {
            state.domainsCaptured.add(domain)
            state.lastUpdated = System.currentTimeMillis()
        }
    }

    fun updateTraffic(mac: String, rx: Long, tx: Long) {
        val state = sessions.getOrPut(mac) { SessionState(mac) }
        synchronized(state) {
            state.rxBytes = rx
            state.txBytes = tx
            state.lastUpdated = System.currentTimeMillis()
        }
    }

    fun recordLatency(mac: String, latency: Int) {
        val state = sessions.getOrPut(mac) { SessionState(mac) }
        synchronized(state) {
            if (latency > 0) {
                state.latencyHistory.add(latency)
                if (state.latencyHistory.size > SessionState.MAX_HISTORY) state.latencyHistory.removeAt(0)
                updateStability(state)
            }
        }
    }

    fun recordActivity(mac: String, score: Float) {
        val state = sessions.getOrPut(mac) { SessionState(mac) }
        synchronized(state) {
            state.activityHistory.add(score)
            if (state.activityHistory.size > SessionState.MAX_HISTORY) state.activityHistory.removeAt(0)
        }
    }

    private fun updateStability(state: SessionState) {
        if (state.latencyHistory.isEmpty()) return
        val variance = state.latencyHistory.let { list ->
            val avg = list.average()
            list.map { (it - avg) * (it - avg) }.average()
        }
        // Higher variance = lower stability
        state.stabilityScore = (1.0f / (1.0f + (variance.toFloat() / 1000f))).coerceIn(0f, 1f)
    }

    fun getStability(mac: String): Float = sessions[mac]?.stabilityScore ?: 1.0f
    fun getLatencyHistory(mac: String): List<Int> = sessions[mac]?.latencyHistory?.toList() ?: emptyList()
    fun getActivityHistory(mac: String): List<Float> = sessions[mac]?.activityHistory?.toList() ?: emptyList()

    fun getTraffic(mac: String): Pair<Long, Long> {
        val state = sessions[mac] ?: return 0L to 0L
        return state.rxBytes to state.txBytes
    }

    fun getDomains(mac: String): List<String> = sessions[mac]?.domainsCaptured?.toList() ?: emptyList()

    /** Cleanup sessions for devices not seen in 24 hours */
    fun pruneStale() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        sessions.entries.removeIf { it.value.lastUpdated < cutoff }
    }
}
