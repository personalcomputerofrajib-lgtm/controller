package com.wifimonitor.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.launch

data class OuiEntry(val prefix: String, val vendor: String)

@Singleton
class MacVendorLookup @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ouiMap = HashMap<String, String>(16384)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var isLoaded = false

    init {
        // Audit 7: Background Load (Zero-Blocking Boot)
        scope.launch {
            loadOuiDatabase()
        }
    }

    private fun loadOuiDatabase() {
        try {
            val json = context.assets.open("oui_database.json")
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<OuiEntry>>() {}.type
            val entries: List<OuiEntry> = Gson().fromJson(json, type)
            entries.forEach { ouiMap[it.prefix.uppercase()] = it.vendor }
            isLoaded = true
            Log.i("MacVendorLookup", "Loaded ${ouiMap.size} OUI entries")
        } catch (e: Exception) {
            Log.w("MacVendorLookup", "Failed to load OUI database: ${e.message}")
            loadFallbackEntries()
            isLoaded = true
        }
    }

    private fun loadFallbackEntries() {
        // Common vendors hardcoded as fallback
        val fallback = mapOf(
            "APPLE" to listOf("A4:C3:F0", "F0:18:98", "3C:22:FB", "BC:D0:74", "34:AB:37", "A8:51:AB"),
            "Samsung" to listOf("8C:77:12", "CC:07:AB", "F8:77:B8", "50:01:BB", "E4:12:1D"),
            "Google" to listOf("F4:F5:D8", "3C:5A:B4", "48:D6:D5", "54:60:09"),
            "Amazon" to listOf("FC:65:DE", "74:75:48", "A0:02:DC", "50:DC:E7"),
            "Xiaomi" to listOf("28:6C:07", "64:09:80", "F8:A4:5F", "AC:C1:EE"),
            "Intel" to listOf("8C:8D:28", "A4:C3:F0", "00:1B:21", "5C:51:4F"),
            "Raspberry Pi" to listOf("DC:A6:32", "B8:27:EB", "E4:5F:01"),
            "TP-Link" to listOf("50:C7:BF", "C0:06:C3", "98:DA:C4", "14:CC:20"),
            "Netgear" to listOf("A0:04:60", "C4:04:15", "20:4E:7F", "80:37:73"),
            "ASUS" to listOf("04:D4:C4", "10:7B:44", "14:DD:A9", "2C:FD:A1")
        )
        fallback.forEach { (vendor, prefixes) ->
            prefixes.forEach { prefix -> ouiMap[prefix.uppercase()] = vendor }
        }
    }

    fun lookup(mac: String): String {
        if (mac.isBlank()) return "Unknown"
        val normalized = mac.uppercase().replace("-", ":")
        // Try 8-char prefix (XX:XX:XX)
        val prefix8 = normalized.take(8)
        ouiMap[prefix8]?.let { return it }
        // Try 6-char prefix (XXXXXX without colons)
        val prefix6 = normalized.replace(":", "").take(6)
        val formatted = "${prefix6.take(2)}:${prefix6.drop(2).take(2)}:${prefix6.drop(4).take(2)}"
        return ouiMap[formatted] ?: "Unknown"
    }
}
