package com.wifimonitor.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Level 7 Persistent Memory Engine.
 * Stores behavioral signatures and known device maps for every network.
 */
@Singleton
class NetworkMemoryRepository @Inject constructor() {

    data class NetworkSignature(
        val ssid: String,
        val knownMacs: MutableSet<String> = mutableSetOf(),
        var typicalActivityLevel: Float = 0f,
        var lastConnected: Long = System.currentTimeMillis()
    )

    private val memory = ConcurrentHashMap<String, NetworkSignature>()

    fun recordSeenDevices(ssid: String?, devices: List<NetworkDevice>) {
        if (ssid == null) return
        val signature = memory.getOrPut(ssid) { NetworkSignature(ssid) }
        
        devices.forEach { signature.knownMacs.add(it.mac) }
        signature.lastConnected = System.currentTimeMillis()
        
        Log.i("NetworkMemory", "Updated memory for $ssid: ${signature.knownMacs.size} known devices")
    }

    fun getKnownMacs(ssid: String?): Set<String> =
        memory[ssid ?: ""]?.knownMacs ?: emptySet()

    fun isNewToThisNetwork(ssid: String?, mac: String): Boolean {
        val signature = memory[ssid ?: ""] ?: return true
        return !signature.knownMacs.contains(mac)
    }
}
