package com.wifimonitor.analyzer

import android.content.Context
import android.os.Environment
import android.util.Log
import com.wifimonitor.data.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data export system — CSV and JSON for all device data, traffic, and history.
 * Files saved to app's external files directory (accessible via file manager).
 */
@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    sealed class ExportResult {
        data class Success(val filePath: String, val recordCount: Int) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    // ── Device Export ──

    fun exportDevicesCsv(devices: List<NetworkDevice>): ExportResult {
        return try {
            val file = createFile("devices", "csv")
            FileWriter(file).use { w ->
                w.appendLine("MAC,IP,Hostname,Manufacturer,Type,Group,Trust,Status,Activity,BehaviorLabel,Latency_ms,Jitter_ms,Reliability_%,Fingerprint,MAC_Randomized,FirstSeen,LastSeen,SessionDuration_ms,TotalSessions,SmartTags,RecentDomains,OpenPorts,Blocked")
                devices.forEach { d ->
                    w.appendLine(listOf(
                        d.mac, d.ip, esc(d.hostname), esc(d.manufacturer),
                        d.deviceType.name, d.deviceGroup.name, d.trustLevel.name,
                        d.status.name, d.activityLevel.name, esc(d.behaviorLabel),
                        d.pingResponseMs, d.jitterMs, d.reliabilityPct,
                        d.fingerprintHash, d.isMacRandomized,
                        timeFmt.format(Date(d.firstSeen)), timeFmt.format(Date(d.lastSeen)),
                        d.sessionDuration, d.totalSessions,
                        esc(d.smartTags), esc(d.recentDomains), esc(d.openPorts), d.isBlocked
                    ).joinToString(","))
                }
            }
            ExportResult.Success(file.absolutePath, devices.size)
        } catch (e: Exception) {
            Log.e(TAG, "Export devices CSV: ${e.message}")
            ExportResult.Error(e.message ?: "Unknown error")
        }
    }

    fun exportDevicesJson(devices: List<NetworkDevice>): ExportResult {
        var file: File? = null
        return try {
            file = createFile("devices", "json")
            file.bufferedWriter().use { w ->
                w.write("[\n")
                devices.forEachIndexed { i, d ->
                    val json = """
                      {
                        "mac": "${d.mac}",
                        "ip": "${d.ip}",
                        "hostname": "${d.hostname}",
                        "manufacturer": "${d.manufacturer}",
                        "deviceType": "${d.deviceType.name}",
                        "deviceGroup": "${d.deviceGroup.name}",
                        "trustLevel": "${d.trustLevel.name}",
                        "status": "${d.status.name}",
                        "activityLevel": "${d.activityLevel.name}",
                        "behaviorLabel": "${d.behaviorLabel}",
                        "latencyMs": ${d.pingResponseMs},
                        "latencyMin": ${d.latencyMin},
                        "latencyMax": ${d.latencyMax},
                        "latencyMedian": ${d.latencyMedian},
                        "jitterMs": ${d.jitterMs},
                        "reliabilityPct": ${d.reliabilityPct},
                        "fingerprintHash": "${d.fingerprintHash}",
                        "macRandomized": ${d.isMacRandomized},
                        "firstSeen": "${timeFmt.format(Date(d.firstSeen))}",
                        "lastSeen": "${timeFmt.format(Date(d.lastSeen))}",
                        "sessionDurationMs": ${d.sessionDuration},
                        "totalSessions": ${d.totalSessions},
                        "smartTags": [${d.smartTagsList().joinToString(",") { "\"$it\"" }}],
                        "recentDomains": [${d.recentDomainsList().joinToString(",") { "\"$it\"" }}],
                        "openPorts": [${d.openPortsList().joinToString(",")}],
                        "blocked": ${d.isBlocked}
                      }${if (i < devices.size - 1) "," else ""}
                    """.trimIndent()
                    w.write(json + "\n")
                }
                w.write("]")
            }
            ExportResult.Success(file.absolutePath, devices.size)
        } catch (e: Exception) {
            // Audit 12: Delete corrupted partial file on disk failure
            try { file?.delete() } catch (_: Exception) {}
            ExportResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Traffic Export ──

    fun exportTrafficCsv(records: List<TrafficRecord>): ExportResult {
        return try {
            val file = createFile("traffic", "csv")
            FileWriter(file).use { w ->
                w.appendLine("Timestamp,DeviceMAC,Domain,Source")
                records.forEach { r ->
                    w.appendLine("${timeFmt.format(Date(r.timestamp))},${r.deviceMac},${esc(r.domain)},${r.source}")
                }
            }
            ExportResult.Success(file.absolutePath, records.size)
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── History Export ──

    fun exportHistoryCsv(history: List<DeviceHistory>): ExportResult {
        return try {
            val file = createFile("history", "csv")
            FileWriter(file).use { w ->
                w.appendLine("Timestamp,MAC,LatencyMs,ActivityScore,ReliabilityPct,Status,JitterMs")
                history.forEach { h ->
                    w.appendLine("${timeFmt.format(Date(h.timestamp))},${h.mac},${h.latencyMs},${h.activityScore},${h.reliabilityPct},${h.status.name},${h.jitterMs}")
                }
            }
            ExportResult.Success(file.absolutePath, history.size)
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun createFile(prefix: String, ext: String): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "wifi_intelligence")
        dir.mkdirs()
        val timestamp = dateFormat.format(Date())
        return File(dir, "${prefix}_${timestamp}.$ext")
    }

    private fun esc(s: String): String = "\"${s.replace("\"", "\"\"")}\""

    companion object { const val TAG = "DataExporter" }
}
