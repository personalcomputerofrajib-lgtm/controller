package com.wifimonitor.analyzer

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic mode — logs raw scan data, probe timings, state transitions.
 * When enabled, provides full transparency for advanced users.
 * Ring buffer keeps last N entries to avoid memory issues.
 */
@Singleton
class DiagnosticLogger @Inject constructor() {

    data class DiagEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val category: Category,
        val message: String,
        val details: String = ""
    ) {
        enum class Category { SCAN, PROBE, STATE, DNS, ERROR, PERF }
    }

    private val buffer = ConcurrentLinkedDeque<DiagEntry>()
    private val bufferSize = java.util.concurrent.atomic.AtomicInteger(0)
    private var _enabled = false
    val isEnabled: Boolean get() = _enabled

    companion object {
        const val MAX_ENTRIES = 500
    }

    fun enable() { _enabled = true; log(DiagEntry.Category.PERF, "Diagnostic mode enabled") }
    fun disable() { log(DiagEntry.Category.PERF, "Diagnostic mode disabled"); _enabled = false }
    fun toggle() { if (_enabled) disable() else enable() }

    fun log(category: DiagEntry.Category, message: String, details: String = "") {
        if (!_enabled && category != DiagEntry.Category.ERROR) return
        val entry = DiagEntry(category = category, message = message, details = details)
        buffer.addFirst(entry)
        val size = bufferSize.incrementAndGet()
        
        // Audit 19: O(1) ring-buffer cleanup (Zero process overhead)
        if (size > MAX_ENTRIES) {
            buffer.removeLast()
            bufferSize.decrementAndGet()
        }
        Log.d("Diag[${category.name}]", "$message ${if (details.isNotBlank()) "| $details" else ""}")
    }

    // Convenience methods
    fun scanStart(targets: Int) = log(DiagEntry.Category.SCAN, "Scan started", "$targets targets")
    fun scanComplete(devices: Int, durationMs: Long) = log(DiagEntry.Category.SCAN, "Scan complete", "$devices devices in ${durationMs}ms")
    fun probeResult(ip: String, latencyMs: Int, success: Boolean) = log(DiagEntry.Category.PROBE, if (success) "✓ $ip ${latencyMs}ms" else "✗ $ip timeout")
    fun stateChange(mac: String, from: String, to: String) = log(DiagEntry.Category.STATE, "State: $mac", "$from → $to")
    fun dnsCapture(domain: String, source: String) = log(DiagEntry.Category.DNS, "DNS: $domain", "via $source")
    fun error(msg: String, e: Exception? = null) = log(DiagEntry.Category.ERROR, "ERROR: $msg", e?.message ?: "")
    fun perf(msg: String) = log(DiagEntry.Category.PERF, msg)

    fun getEntries(limit: Int = 100, category: DiagEntry.Category? = null): List<DiagEntry> {
        val list = buffer.toList()
        return if (category != null) list.filter { it.category == category }.take(limit)
        else list.take(limit)
    }

    fun clear() = buffer.clear()

    /** Export diagnostics as text for sharing */
    fun exportAsText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== WiFi Intelligence Diagnostic Log ===")
        sb.appendLine("Exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("Entries: ${buffer.size}")
        sb.appendLine()
        val fmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        for (entry in buffer) {
            sb.appendLine("[${fmt.format(java.util.Date(entry.timestamp))}] [${entry.category.name}] ${entry.message}")
            if (entry.details.isNotBlank()) sb.appendLine("  └─ ${entry.details}")
        }
        return sb.toString()
    }
}
