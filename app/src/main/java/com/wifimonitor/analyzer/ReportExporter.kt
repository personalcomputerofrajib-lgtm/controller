package com.wifimonitor.analyzer

import com.wifimonitor.data.DeviceRepository
import com.wifimonitor.data.NetworkDevice
import com.wifimonitor.data.TrafficRecord
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forensics Report Exporter.
 * Generates detailed CSV reports of network activity, device sessions, and identified traffic.
 */
@Singleton
class ReportExporter @Inject constructor(
    private val repository: DeviceRepository
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    suspend fun generateTrafficCsv(): String {
        val traffic = repository.getRecentTrafficSnapshot()
        val sb = StringBuilder()
        sb.append("Timestamp,Device MAC,Domain,Source,Type\n")
        
        traffic.forEach { record ->
            val time = dateFmt.format(Date(record.timestamp))
            sb.append("$time,${record.deviceMac},${record.domain},${record.source},${record.type}\n")
        }
        
        return sb.toString()
    }

    suspend fun generateDeviceInventoryCsv(devices: List<NetworkDevice>): String {
        val sb = StringBuilder()
        sb.append("Name,MAC,IP,Manufacturer,Type,Status,Last Seen,Trust\n")
        
        devices.forEach { d ->
            val lastSeen = dateFmt.format(Date(d.lastSeen))
            sb.append("${d.displayName},${d.mac},${d.ip},${d.manufacturer},${d.deviceType},${d.status},$lastSeen,${d.trustLevel}\n")
        }
        
        return sb.toString()
    }

    suspend fun generateSecurityAuditReport(devices: List<NetworkDevice>, alerts: List<com.wifimonitor.data.AlertRecord> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("--- NETWORK SECURITY AUDIT REPORT ---\n")
        sb.append("Generated on: ${dateFmt.format(Date())}\n\n")
        
        sb.append("SUMMARY:\n")
        sb.append("Total Devices: ${devices.size}\n")
        sb.append("Online Now: ${devices.count { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }}\n")
        sb.append("Anomalies Detected: ${alerts.count { it.type == com.wifimonitor.data.AlertType.ANOMALY }}\n\n")
        
        sb.append("ALERTS LOG:\n")
        alerts.take(20).forEach { alert ->
            sb.append("[${dateFmt.format(Date(alert.timestamp))}] ${alert.type}: ${alert.message}\n")
        }
        
        return sb.toString()
    }
}
