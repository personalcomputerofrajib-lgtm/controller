package com.wifimonitor.analyzer

import android.util.Log
import com.wifimonitor.data.AlertRecord
import com.wifimonitor.data.AlertType
import com.wifimonitor.data.NetworkDevice
import com.wifimonitor.scanner.PortScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraDetectorEngine @Inject constructor(
    private val portScanner: PortScanner
) {
    private val tag = "CameraDetectorEngine"
    
    // Top Offline MAC Prefixes for IP Cameras (Hardcoded for immediate processing)
    private val cameraMacPrefixes = mapOf(
        "2C:AA:8E" to "Wyze Labs",
        "7C:78:B2" to "Wyze Labs",
        "C8:02:8F" to "Ring",
        "A4:DA:22" to "Ring",
        "B0:C5:54" to "Hikvision",
        "38:A4:ED" to "Hikvision",
        "E4:24:6C" to "Dahua",
        "BC:1C:81" to "Dahua",
        "90:B6:86" to "Amcrest",
        "00:1E:E3" to "Nest",
        "18:B4:30" to "Nest"
    )

    data class ScanReport(
        val isCamera: Boolean,
        val manufacturer: String?,
        val openPorts: List<Int>,
        val severity: String, // "HIGH", "SUSPICIOUS", "SAFE"
        val message: String
    )

    /**
     * Executes a stealth port scan targeting RTSP and HTTP ports,
     * while comparing the MAC against the known camera vendor database.
     */
    suspend fun analyzeDevice(device: NetworkDevice): ScanReport = withContext(Dispatchers.IO) {
        val macPrefix = device.mac.substring(0, java.lang.Math.min(8, device.mac.length)).uppercase()
        val manufacturer = cameraMacPrefixes[macPrefix]
        
        // Scan for explicitly suspicious ports: 554 (RTSP), 80 (HTTP), 8080 (HTTP-Alt), 1935 (RTMP), 5000 (UPnP)
        val portResult = try {
            portScanner.scanDevice(device.ip, timeoutMs = 250)
        } catch(e: Exception) {
            Log.e(tag, "Failed to port scan: ${device.ip}")
            null
        }

        val openPorts = portResult?.openPorts ?: emptyList()
        val hasVideoPorts = openPorts.contains(554) || openPorts.contains(1935)
        val hasWebInterface = openPorts.contains(80) || openPorts.contains(8080)
        
        if (manufacturer != null && hasVideoPorts) {
            return@withContext ScanReport(
                isCamera = true,
                manufacturer = manufacturer,
                openPorts = openPorts,
                severity = "HIGH",
                message = "Hidden Camera ($manufacturer) detected streaming natively via RTSP."
            )
        } else if (manufacturer != null) {
            return@withContext ScanReport(
                isCamera = true,
                manufacturer = manufacturer,
                openPorts = openPorts,
                severity = "SUSPICIOUS",
                message = "Device matches $manufacturer camera vendor prefix. Verify physical presence."
            )
        } else if (hasVideoPorts) {
            return@withContext ScanReport(
                isCamera = true,
                manufacturer = "Unknown Vendor",
                openPorts = openPorts,
                severity = "HIGH",
                message = "Device is exposing RTSP/RTMP Video streaming ports. Likely an IP Camera."
            )
        }
        
        return@withContext ScanReport(
            isCamera = false,
            manufacturer = null,
            openPorts = openPorts,
            severity = "SAFE",
            message = "No hidden camera signatures detected."
        )
    }
}
