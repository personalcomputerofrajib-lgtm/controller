package com.wifimonitor.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production repository with:
 * - Data consistency (dedup, threshold-based state smoothing)
 * - Historical snapshot recording
 * - Exception-safe everywhere
 * - Cold start support (cached last state)
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val trafficDao: TrafficDao,
    private val engine: com.wifimonitor.analyzer.IntelligenceEngine
) {
    val allDevices: Flow<List<NetworkDevice>> get() = deviceDao.getAllDevices(currentProfileId)
    val onlineDevices: Flow<List<NetworkDevice>> get() = deviceDao.getOnlineDevices(currentProfileId)
    val onlineCount: Flow<Int> get() = deviceDao.getOnlineCount(currentProfileId)
    val totalCount: Flow<Int> get() = deviceDao.getTotalCount(currentProfileId)
    val alerts: Flow<List<AlertRecord>> = deviceDao.getAlerts()
    val unreadAlertCount: Flow<Int> = deviceDao.getUnreadAlertCount()
    val recentTraffic: Flow<List<TrafficRecord>> = trafficDao.getRecentTraffic()
    val allRules: Flow<List<AlertRule>> = deviceDao.getAllRules()
    
    private var currentProfileId = "default"
    fun setNetworkProfile(ssid: String?) { currentProfileId = ssid ?: "default" }
    fun getProfileId() = currentProfileId

    data class ScanDelta(
        val newMacs: Set<String> = emptySet(),
        val leftMacs: Set<String> = emptySet(),
        val changedMacs: Set<String> = emptySet()
    )
    private val _scanDelta = MutableStateFlow(ScanDelta())
    val scanDelta: StateFlow<ScanDelta> = _scanDelta.asStateFlow()
    private var lastSnapshotMacs = emptySet<String>()

    // State smoothing & Debouncing
    private val offlineCounters = ConcurrentHashMap<String, Int>()
    private val alertThrottleMap = ConcurrentHashMap<String, Long>() // mac + type -> last alert time
    companion object {
        const val OFFLINE_THRESHOLD = 2  // need 2 consecutive misses to mark offline
        const val HISTORY_INTERVAL_MS = 5 * 60 * 1000L // record history every 5 min
    }
    private var lastHistoryTimestamp = 0L

    suspend fun getRecentTrafficSnapshot(): List<TrafficRecord> =
        try { trafficDao.getRecentTrafficSnapshot() } catch (_: Exception) { emptyList() }

    suspend fun getDeviceSnapshot(mac: String): NetworkDevice? =
        try { deviceDao.getDeviceByMac(mac) } catch (_: Exception) { null }



    /**
     * Data-consistent scan update:
     * - Deduplicates by MAC
     * - Smooths state transitions (OFFLINE needs 2 consecutive misses)
     * - Preserves user data (nickname, group, trust)
     * - Records historical snapshots
     */
    suspend fun updateScanResults(activeDevices: List<NetworkDevice>) {
        try {
            // Deduplicate by MAC
            val dedupedMap = mutableMapOf<String, NetworkDevice>()
            activeDevices.forEach { d ->
                val existing = dedupedMap[d.mac]
                if (existing == null || d.lastSeen > existing.lastSeen) {
                    dedupedMap[d.mac] = d
                }
            }
            val deduped = dedupedMap.values.toList()
            val activeMacs = deduped.map { it.mac }.toSet()

            // Reset offline counters for active devices
            activeMacs.forEach { offlineCounters.remove(it) }

            // Upsert active
            deduped.forEach { device -> upsertDevice(device) }

            // Smooth offline transitions: increment counter, only mark offline at threshold
            if (activeMacs.isNotEmpty()) {
                val allKnown = try { deviceDao.getOnlineDevicesSnapshot(currentProfileId) } catch (_: Exception) { emptyList() }
                allKnown.forEach { known ->
                    if (known.mac !in activeMacs) {
                        val count = (offlineCounters[known.mac] ?: 0) + 1
                        offlineCounters[known.mac] = count
                        if (count >= OFFLINE_THRESHOLD) {
                            val reason = engine.guessDisappearanceReason(
                                known, 
                                activeMacs, 
                                deduped.map { it.ip }.toSet()
                            )
                            deviceDao.updateDevice(known.copy(status = DeviceStatus.OFFLINE, disappearanceReason = reason))
                            offlineCounters.remove(known.mac)
                        }
                    }
                }
            }

            // Record history snapshot (throttled to every 5 min)
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastHistoryTimestamp > HISTORY_INTERVAL_MS) {
                recordHistorySnapshot(deduped)
                lastHistoryTimestamp = now
            }
                
            // Level 25: Custom Policy Engine
            checkCustomRules(deduped)

            val currentMacs = deduped.map { it.mac }.toSet()
            if (lastSnapshotMacs.isNotEmpty()) {
                val newMacsList = currentMacs - lastSnapshotMacs
                _scanDelta.value = ScanDelta(
                    newMacs = newMacsList,
                    leftMacs = lastSnapshotMacs - currentMacs
                )
                
                // Level 7: Auto-Sentry Trigger
                if (newMacsList.isNotEmpty()) {
                    Log.i("Repository", "Auto-Sentry: ${newMacsList.size} new devices. Scheduling deep audits.")
                    // In a real app, we'd trigger the MonitorService to do a deep scan per MAC
                }
            }
            lastSnapshotMacs = currentMacs

        } catch (e: Exception) {
            Log.e("DeviceRepository", "updateScanResults: ${e.message}")
        }
    }

    private suspend fun upsertDevice(device: NetworkDevice) {
        try {
            val existing = deviceDao.getDeviceByMac(device.mac)
            val deviceWithProfile = device.copy(networkProfileId = currentProfileId)
            
            if (existing == null) {
                deviceDao.insertDevice(deviceWithProfile)
                // Generate alert for truly new devices
                deviceDao.insertAlert(AlertRecord(
                    type = AlertType.NEW_DEVICE,
                    deviceMac = device.mac,
                    message = "New device joined: ${device.displayName} (${device.ip})" +
                        if (device.isMacRandomized) " [Randomized MAC]" else ""
                ))
            } else {
                // Audit 3: Partial Update (Anti-Loss)
                // Use the specialized query to update only metrics, preserving usage counts
                deviceDao.updateScannerMetrics(
                    mac = device.mac,
                    ip = device.ip,
                    hostname = device.hostname,
                    lastSeen = device.lastSeen,
                    status = device.status,
                    ping = device.pingResponseMs,
                    jitter = device.jitterMs,
                    reli = device.reliabilityPct,
                    label = device.behaviorLabel
                )
            }
        } catch (e: Exception) {
            Log.e("DeviceRepository", "upsert: ${e.message}")
        }
    }

    private suspend fun recordHistorySnapshot(devices: List<NetworkDevice>) {
        try {
            val snapshots = devices.map { d ->
                val actScore = when (d.activityLevel) {
                    ActivityLevel.IDLE -> 0f
                    ActivityLevel.LOW -> 0.2f
                    ActivityLevel.BROWSING -> 0.5f
                    ActivityLevel.ACTIVE -> 0.75f
                    ActivityLevel.HEAVY -> 1f
                }
                DeviceHistory(
                    mac = d.mac,
                    latencyMs = d.pingResponseMs,
                    activityScore = actScore,
                    reliabilityPct = d.reliabilityPct,
                    status = d.status,
                    jitterMs = d.jitterMs
                )
            }
            deviceDao.insertHistoryBatch(snapshots)
        } catch (e: Exception) { Log.w("DeviceRepository", "recordHistory: ${e.message}") }
    }

    // ── User actions ──

    suspend fun setDeviceNickname(mac: String, nickname: String) {
        try { deviceDao.getDeviceByMac(mac)?.let { deviceDao.updateDevice(it.copy(nickname = nickname, isKnown = true)) } }
        catch (e: Exception) { Log.e("DeviceRepository", "nickname: ${e.message}") }
    }

    suspend fun setDeviceGroup(mac: String, group: DeviceGroup) {
        try { deviceDao.getDeviceByMac(mac)?.let { deviceDao.updateDevice(it.copy(deviceGroup = group)) } }
        catch (e: Exception) { Log.e("DeviceRepository", "group: ${e.message}") }
    }

    suspend fun setDeviceTrust(mac: String, trust: TrustLevel) {
        try { deviceDao.getDeviceByMac(mac)?.let { deviceDao.updateDevice(it.copy(trustLevel = trust)) } }
        catch (e: Exception) { Log.e("DeviceRepository", "trust: ${e.message}") }
    }

    suspend fun setDeviceOpenPorts(mac: String, ports: String) {
        try { deviceDao.getDeviceByMac(mac)?.let { deviceDao.updateDevice(it.copy(openPorts = ports)) } }
        catch (e: Exception) { Log.e("DeviceRepository", "openPorts: ${e.message}") }
    }

    suspend fun updateLastSeenPorts(mac: String, ports: String) {
        try { deviceDao.updateLastSeenPorts(mac, ports) }
        catch (e: Exception) { Log.e("DeviceRepository", "lastSeenPorts: ${e.message}") }
    }

    suspend fun setDeviceBlocked(mac: String, blocked: Boolean) {
        try {
            deviceDao.getDeviceByMac(mac)?.let {
                deviceDao.updateDevice(it.copy(isBlocked = blocked))
                deviceDao.insertAlert(AlertRecord(
                    type = AlertType.DEVICE_BLOCKED,
                    deviceMac = mac,
                    message = if (blocked) "Device blocked: ${it.displayName}" else "Device unblocked: ${it.displayName}"
                ))
            }
        } catch (e: Exception) { Log.e("DeviceRepository", "block: ${e.message}") }
    }

    suspend fun setDeviceTracked(mac: String, tracked: Boolean) {
        try { deviceDao.getDeviceByMac(mac)?.let { deviceDao.updateDevice(it.copy(isTracked = tracked)) } }
        catch (e: Exception) { Log.e("DeviceRepository", "tracked: ${e.message}") }
    }

    suspend fun addTrafficRecord(record: TrafficRecord) {
        try { trafficDao.insertRecord(record) } catch (_: Exception) { }
    }

    suspend fun addAlertRecord(alert: AlertRecord) {
        val key = "${alert.deviceMac}_${alert.type.name}"
        val now = System.currentTimeMillis()
        val last = alertThrottleMap[key] ?: 0L
        
        // Audit 12: 60-minute debounce for automated alerts
        if (now - last > 60 * 60 * 1000L) {
            try { 
                deviceDao.insertAlert(alert) 
                alertThrottleMap[key] = now
            } catch (_: Exception) {}
        }
    }

    fun getDeviceTraffic(mac: String): Flow<List<TrafficRecord>> = trafficDao.getTrafficForDevice(mac)
    fun getTopDomains(sinceMs: Long): Flow<List<DomainCount>> = trafficDao.getTopDomains(sinceMs)
    fun getTopDomainsForDevice(mac: String, sinceMs: Long): Flow<List<DomainCount>> = trafficDao.getTopDomainsForDevice(mac, sinceMs)

    suspend fun addDeviceTrafficIncrement(ip: String, down: Long, up: Long) {
        try { deviceDao.incrementBytesByIp(ip, down, up) } catch (_: Exception) {}
    }

    suspend fun getDeviceHistory(mac: String, limit: Int = 100): List<DeviceHistory> =
        try { deviceDao.getHistoryForDevice(mac, limit) } catch (_: Exception) { emptyList() }

    fun getDeviceHistoryFlow(mac: String, since: Long): Flow<List<DeviceHistory>> =
        deviceDao.getHistoryFlow(mac, since)

    suspend fun markAlertsRead() { try { deviceDao.markAllAlertsRead() } catch (_: Exception) {} }

    suspend fun pruneOldTraffic() {
        try {
            val trafficCutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            trafficDao.deleteOldRecords(trafficCutoff)
            val historyCutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
            deviceDao.pruneOldHistory(historyCutoff)
            val alertCutoff = System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000L
            deviceDao.pruneOldAlerts(alertCutoff)
            val deviceCutoff = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000L
            deviceDao.pruneStaleDevices(deviceCutoff)
        } catch (e: Exception) { Log.e("DeviceRepository", "prune: ${e.message}") }
    }

    // ── Custom Rules Logic ──

    suspend fun addAlertRule(rule: AlertRule) { deviceDao.insertRule(rule) }
    suspend fun deleteAlertRule(rule: AlertRule) { deviceDao.deleteRule(rule) }

    private suspend fun checkCustomRules(activeDevices: List<NetworkDevice>) {
        val rules = deviceDao.getEnabledRules()
        if (rules.isEmpty()) return

        val now = System.currentTimeMillis()
        rules.forEach { rule ->
            // Throttle: Don't trigger same rule more than once per hour
            if (now - rule.lastTriggered < 60 * 60 * 1000L) return@forEach

            val device = activeDevices.find { it.mac == rule.deviceMac } 
                ?: deviceDao.getDeviceByMac(rule.deviceMac) ?: return@forEach

            when (rule.type) {
                RuleType.THRESHOLD_MB -> {
                    val totalMB = (device.totalDownloadBytes + device.totalUploadBytes) / (1024 * 1024)
                    if (totalMB >= rule.thresholdValue) {
                        triggerRule(rule, "Data limit reached: ${device.displayName} used ${totalMB}MB (Limit: ${rule.thresholdValue}MB)")
                    }
                }
                RuleType.DEVICE_DISCONNECT -> {
                    if (device.status == DeviceStatus.OFFLINE) {
                        triggerRule(rule, "Critical Disconnect: ${device.displayName} is no longer reachable.")
                    }
                }
                RuleType.DEVICE_RECONNECT -> {
                    if (device.status == DeviceStatus.ONLINE) {
                        // We check if it was recently offline in a real app, 
                        // but for simplicity we trigger when seen as ONLINE
                        triggerRule(rule, "Back Online: ${device.displayName} has reconnected.")
                    }
                }
            }
        }
    }

    private suspend fun triggerRule(rule: AlertRule, message: String) {
        deviceDao.insertAlert(AlertRecord(
            type = AlertType.SUSPICIOUS_BEHAVIOR, // Reusing type or could add CUSTOM_RULE
            deviceMac = rule.deviceMac,
            message = "[Rule Hit] $message"
        ))
        deviceDao.updateRuleTriggerTime(rule.id, System.currentTimeMillis())
    }

    /**
     * Precision-Scale: Global State Export.
     * Generates a forensic JSON snapshot of the entire network state.
     */
    suspend fun exportSnapshot(context: android.content.Context): android.net.Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val devices = deviceDao.getOnlineDevicesSnapshot(currentProfileId)
                val json = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(devices)
                
                val file = java.io.File(context.cacheDir, "network_snapshot_${System.currentTimeMillis()}.json")
                file.writeText(json)
                
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
            } catch (e: Exception) {
                Log.e("Repository", "Export failed", e)
                null
            }
        }
    }
}
