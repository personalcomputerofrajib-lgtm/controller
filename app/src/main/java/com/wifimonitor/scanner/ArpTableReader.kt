package com.wifimonitor.scanner

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArpTableReader @Inject constructor() {

    private val tag = "ArpTableReader"

    /**
     * Reads /proc/net/arp — the Linux kernel's ARP cache.
     * Available on all Android devices without root.
     *
     * Format:
     * IP address       HW type     Flags       HW address            Mask     Device
     * 192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
     */
    fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val flags = parts[2]
                        val mac = parts[3]

                        // Flags 0x2 = valid complete entry, skip incomplete (0x0)
                        if (flags != "0x0" && mac != "00:00:00:00:00:00" && isValidMac(mac)) {
                            result[ip] = mac.uppercase()
                            Log.v(tag, "ARP: $ip -> $mac")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to read ARP table: ${e.message}")
        }
        Log.i(tag, "ARP table read: ${result.size} entries")
        return result
    }

    private fun isValidMac(mac: String): Boolean {
        return mac.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"))
    }

    companion object {
        /** Static convenience for quick ARP lookups without DI */
        fun getArpTable(): Map<String, String> {
            val result = mutableMapOf<String, String>()
            try {
                BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                    reader.readLine() // skip header
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val parts = line!!.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val ip = parts[0]
                            val flags = parts[2]
                            val mac = parts[3]
                            if (flags != "0x0" && mac != "00:00:00:00:00:00" && mac.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"))) {
                                result[ip] = mac.uppercase()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return result
        }
    }
}
