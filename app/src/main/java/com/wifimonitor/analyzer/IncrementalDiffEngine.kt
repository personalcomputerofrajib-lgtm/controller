package com.wifimonitor.analyzer

import com.wifimonitor.data.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 7 Performance Optimization Component.
 * Computes differences between scan cycles to minimize processing and UI updates.
 */
@Singleton
class IncrementalDiffEngine @Inject constructor() {
    
    private var lastSnapshot = mutableMapOf<String, DeviceStateSummary>()

    data class DeviceStateSummary(
        val mac: String,
        val status: com.wifimonitor.data.DeviceStatus,
        val activityLevel: com.wifimonitor.data.ActivityLevel,
        val ip: String
    )

    data class DiffResult(
        val updatedDevices: List<NetworkDevice>,
        val changedMacs: Set<String>
    )

    /**
     * Identifies which devices have actually changed since the last pulse.
     */
    fun computeDiff(currentDevices: List<NetworkDevice>): DiffResult {
        val changedMacs = mutableSetOf<String>()
        
        currentDevices.forEach { device ->
            val currentState = DeviceStateSummary(device.mac, device.status, device.activityLevel, device.ip)
            val lastState = lastSnapshot[device.mac]
            
            if (lastState == null || lastState != currentState) {
                changedMacs.add(device.mac)
                lastSnapshot[device.mac] = currentState
            }
        }
        
        return DiffResult(currentDevices, changedMacs)
    }

    /**
     * Clears cache on network switch.
     */
    fun clear() {
        lastSnapshot.clear()
    }
}
