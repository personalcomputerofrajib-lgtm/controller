package com.wifimonitor.data

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.CopyOnWriteArrayList

enum class ChangeType {
    JOINED, LEFT, IP_CHANGED, SPIKE, WAKE, SLEEP, PORT_TRANSITION, ANOMALY
}

data class ChangeEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChangeType,
    val deviceMac: String,
    val message: String,
    val severity: Int = 0 // 0: Normal, 1: Warning, 2: Critical
)

/**
 * Repository for the Network Change Log (Timeline).
 * Maintains a high-fidelity record of all network behavioral transitions.
 */
@Singleton
class ChangeLogRepository @Inject constructor() {
    private val events = CopyOnWriteArrayList<ChangeEvent>()
    private val MAX_EVENTS = 1000

    fun addEvent(type: ChangeType, mac: String, message: String, severity: Int = 0) {
        val event = ChangeEvent(type = type, deviceMac = mac, message = message, severity = severity)
        events.add(0, event) // Add to top
        if (events.size > MAX_EVENTS) {
            events.removeAt(events.size - 1)
        }
    }

    fun getEvents(): List<ChangeEvent> = events.toList()

    fun getEventsForDevice(mac: String): List<ChangeEvent> = 
        events.filter { it.deviceMac == mac }

    fun clear() {
        events.clear()
    }
}
