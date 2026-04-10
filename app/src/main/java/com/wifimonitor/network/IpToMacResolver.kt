package com.wifimonitor.network

import com.wifimonitor.scanner.ArpTableReader
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 8 Infrastructure Component.
 * Bridges ephemeral IP addresses to persistent MAC identities 
 * using the real-time ARP cache.
 */
@Singleton
class IpToMacResolver @Inject constructor() {
    
    private val ipMacCache = ConcurrentHashMap<String, String>()

    /**
     * Resolves an IP to its current MAC address.
     * Checks local cache first, then refreshes from ARP table if needed.
     */
    fun resolve(ip: String): String? {
        val cached = ipMacCache[ip]
        if (cached != null) return cached
        
        // Refresh cache from kernel ARP table
        refreshCache()
        return ipMacCache[ip]
    }

    private fun refreshCache() {
        try {
            val table = ArpTableReader.getArpTable()
            table.forEach { (ip, mac) ->
                if (mac != "00:00:00:00:00:00") {
                    ipMacCache[ip] = mac.uppercase()
                }
            }
        } catch (_: Exception) {}
    }

    fun updateCache(ip: String, mac: String) {
        ipMacCache[ip] = mac.uppercase()
    }
}
