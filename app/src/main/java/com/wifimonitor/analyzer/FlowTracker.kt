package com.wifimonitor.analyzer

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class FlowRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sourceIp: String,
    val destIp: String,
    val destPort: Int,
    val protocol: String = "TCP",
    val startTime: Long = System.currentTimeMillis(),
    @Volatile var lastSeen: Long = System.currentTimeMillis(),
    val bytesRead: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(0),
    @Volatile var sni: String? = null,
    @Volatile var classification: String = "Pending"
) {
    val durationMs: Long get() = lastSeen - startTime
}

/**
 * Level 7 Flow Analysis Engine.
 * Maintains lightweight records of all active network flows.
 */
@Singleton
class FlowTracker @Inject constructor(
    private val classificationEngine: TrafficClassificationEngine
) {
    // Audit 9: Allocation-free keys
    private data class FlowKey(val src: String, val dst: String, val port: Int)
    private val activeFlows = ConcurrentHashMap<FlowKey, FlowRecord>()

    fun recordFlow(sourceIp: String, destIp: String, destPort: Int, sni: String? = null) {
        val key = FlowKey(sourceIp, destIp, destPort)
        val flow = activeFlows.getOrPut(key) {
            FlowRecord(sourceIp = sourceIp, destIp = destIp, destPort = destPort, sni = sni)
        }
        flow.lastSeen = System.currentTimeMillis()
        if (sni != null) flow.sni = sni
    }

    fun updateFlowActivity(sourceIp: String, destIp: String, destPort: Int, bytes: Long) {
        val key = FlowKey(sourceIp, destIp, destPort)
        activeFlows[key]?.let { flow ->
            flow.lastSeen = System.currentTimeMillis()
            val total = flow.bytesRead.addAndGet(bytes)
            
            // Re-classify based on updated metrics
            val sig = classificationEngine.classify(
                flow.sourceIp,
                0.5f, // Simplified burstiness for demo
                flow.durationMs / 1000,
                total
            )
            flow.classification = sig.type.name
        }
    }

    fun getActiveFlowsForIp(ip: String): List<FlowRecord> =
        activeFlows.values.filter { it.sourceIp == ip }

    fun pruneOldFlows() {
        val cutoff = System.currentTimeMillis() - 300_000 // 5 minutes inactivity
        activeFlows.entries.removeIf { it.value.lastSeen < cutoff }
    }
}
