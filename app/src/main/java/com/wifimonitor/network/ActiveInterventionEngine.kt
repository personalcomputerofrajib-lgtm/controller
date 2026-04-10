package com.wifimonitor.network

import android.util.Log
import com.wifimonitor.rules.RuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveInterventionEngine @Inject constructor(
    private val ruleEngine: RuleEngine,
    private val repository: com.wifimonitor.data.DeviceRepository
) {
    private val tag = "InterventionEngine"

    /**
     * Attempts to quarantine a device.
     * Uses Root ARP Spoofing if available, falls back to DNS/Walled Garden.
     */
    suspend fun quarantineDevice(mac: String, ip: String, gatewayIp: String) = withContext(Dispatchers.IO) {
        val hasRoot = checkRootAccess()
        
        // 1. Mark in repository
        repository.setDeviceBlocked(mac, true)

        if (hasRoot) {
            Log.i(tag, "Root detected. Deploying physical ARP Poisoning for $mac")
            executeArpSpoof(mac, ip, gatewayIp)
        } else {
            Log.i(tag, "Non-root mode. Deploying Logic-level Quarantine via DNS.")
            // Non-root: we rely on our DnsServer and ProxyServer
            ruleEngine.addRule(RuleEngine.Rule(
                id = "quarantine_$mac",
                type = RuleEngine.RuleType.GLOBAL_BLOCK,
                deviceMac = mac,
                target = "QUARANTINE",
                action = RuleEngine.Action.BLOCK
            ))
        }
    }

    /**
     * Releases a device from quarantine.
     */
    suspend fun liftQuarantine(mac: String) = withContext(Dispatchers.IO) {
        repository.setDeviceBlocked(mac, false)
        
        // Remove logic rules
        ruleEngine.removeRule("quarantine_$mac")
        
        if (checkRootAccess()) {
            // Restore ARP state by sending correct packets (simplified for MVP)
            Log.i(tag, "Reclaiming ARP state for $mac")
        }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes the physical ARP spoofing command.
     * Note: This assumes some low-level networking capabilities or binary is available via su.
     * For a production app, this would be a compiled C binary for packet injection.
     */
    private fun executeArpSpoof(mac: String, ip: String, gatewayIp: String) {
        try {
            // Placeholder for the actual packet injection command
            // In a real scenario: Runtime.getRuntime().exec(arrayOf("su", "-c", "arpspoof-binary $ip $gatewayIp"))
            Log.d(tag, "Spoofing: Target $ip perceives our MAC as $gatewayIp")
        } catch (e: Exception) {
            Log.e(tag, "ARP Spoof failed: ${e.message}")
        }
    }
}
