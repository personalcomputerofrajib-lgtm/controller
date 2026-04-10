package com.wifimonitor.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wifimonitor.analyzer.*
import com.wifimonitor.data.*
import com.wifimonitor.scanner.ArpTableReader
import com.wifimonitor.scanner.DnsMonitor
import com.wifimonitor.scanner.MdnsDiscovery
import com.wifimonitor.scanner.NetworkScanner
import com.wifimonitor.network.SecureCredentialStore
import com.wifimonitor.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {

    @Inject lateinit var networkScanner: NetworkScanner
    @Inject lateinit var mdnsDiscovery: MdnsDiscovery
    @Inject lateinit var dnsMonitor: DnsMonitor
    @Inject lateinit var repository: DeviceRepository
    @Inject lateinit var behaviorEngine: BehaviorEngine
    @Inject lateinit var signalProcessor: SignalProcessor
    @Inject lateinit var sessionTracker: SessionTracker
    @Inject lateinit var patternLearner: PatternLearner
    @Inject lateinit var diagnostics: DiagnosticLogger
    @Inject lateinit var interfaceMonitor: NetworkInterfaceMonitor
    @Inject lateinit var credentialStore: SecureCredentialStore
    @Inject lateinit var routerApiClient: com.wifimonitor.network.RouterApiClient
    @Inject lateinit var dnsServer: com.wifimonitor.network.DnsServer
    @Inject lateinit var proxyServer: com.wifimonitor.network.ProxyServer
    @Inject lateinit var intelligenceEngine: IntelligenceEngine
    @Inject lateinit var changeLog: ChangeLogRepository
    @Inject lateinit var fingerprinter: NetworkFingerprintEngine
    @Inject lateinit var inferenceEngine: InferenceEngine
    @Inject lateinit var reputationEngine: ReputationEngine
    @Inject lateinit var rcaAnalyzer: RootCauseAnalyzer
    @Inject lateinit var congestionDetector: CongestionSourceDetector
    @Inject lateinit var diffEngine: IncrementalDiffEngine
    @Inject lateinit var networkMemory: NetworkMemoryRepository
    @Inject lateinit var flowTracker: FlowTracker
    @Inject lateinit var cameraDetector: CameraDetectorEngine
    @Inject lateinit var presenceManager: PresenceManager
    @Inject lateinit var walledGarden: com.wifimonitor.network.WalledGardenServer
    @Inject lateinit var arpTableReader: com.wifimonitor.scanner.ArpTableReader
    @Inject lateinit var ispDiagnosticEngine: IspDiagnosticEngine

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Uncaught: ${e.message}", e)
            diagnostics.error("Uncaught coroutine", e as? Exception)
        }
    )
    private var scanJob: Job? = null
    private var dnsJob: Job? = null
    private var sentinelJob: Job? = null
    private var ispJob: Job? = null
    private var isFirstScan = true
    private var scanCount = 0
    private var lastSsid: String? = null
    private var lastHistoryTimestamp = 0L
    
    // Integrity Stats
    private var protectionStartTime = 0L
    private var isAppInForeground = false
    // Level 13: Signal-based scanning (Fixes thrashing)
    private val scanTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    // Level 13: Battery-Aware Throttling
    private var isBatteryLow = false
    private var isCharging = false
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                isBatteryLow = it.action == Intent.ACTION_BATTERY_LOW || 
                    (it.action == Intent.ACTION_BATTERY_CHANGED && it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) < 20)
                isCharging = it.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                Log.i(TAG, "Power State: Low=$isBatteryLow, Charging=$isCharging")
            }
        }
    }

    companion object {
        const val TAG = "MonitorService"
        const val CHANNEL_ID = "wifi_monitor_channel"
        const val CHANNEL_ALERT_ID = "wifi_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val PRUNE_INTERVAL_MS = 3_600_000L
        const val ACTION_FOREGROUND_CHANGED = "action_foreground_changed"
        const val EXTRA_IS_FOREGROUND = "is_foreground"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
        fun setAppForeground(context: Context, isForeground: Boolean) {
            val intent = Intent(context, MonitorService::class.java).apply {
                action = ACTION_FOREGROUND_CHANGED
                putExtra(EXTRA_IS_FOREGROUND, isForeground)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        interfaceMonitor.startMonitoring()
        if (credentialStore.isDiagnosticMode()) diagnostics.enable()
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FOREGROUND_CHANGED) {
            isAppInForeground = intent.getBooleanExtra(EXTRA_IS_FOREGROUND, false)
            if (isAppInForeground) {
                // Resume high-power jobs immediately
                startDnsMonitor()
                startIspDiagnostics()
                scanTrigger.tryEmit(Unit)
            }
        }
        
        if (protectionStartTime == 0L) protectionStartTime = System.currentTimeMillis()
        
        startForeground(NOTIFICATION_ID, buildNotification("Encryption & Security Active"))
        startScanLoop()
        startArpSentinel()
        startDnsMonitor()
        handleGatewayMode()
        startIspDiagnostics()
        
        // Level 8: Auto-Start Traffic Audit VPN
        if (credentialStore.isGatewayMode()) {
            startTrafficAuditVpn()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        dnsServer.stop()
        proxyServer.stop()
        walledGarden.stop()
        interfaceMonitor.stopMonitoring()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startScanLoop() {
        scanJob?.cancel()
        scanJob = scope.launch {
            var pruneTimer = 0L
            val scanInterval = credentialStore.getScanIntervalMs()

            while (isActive) {
                // Wait for either the tick OR an external trigger (e.g. Sentinel)
                try {
                    val adaptiveInterval = when {
                        isCharging -> scanInterval
                        isBatteryLow -> scanInterval * 5 // Extreme throttle (e.g. 5 mins)
                        else -> (scanInterval * 1.5).toLong() // Regular optimization
                    }
                    
                    withTimeoutOrNull(adaptiveInterval) {
                        scanTrigger
                            .debounce(500) // Audit 5: Prevent discovery thrashing
                            .collect { } 
                    }
                } catch (_: Exception) {}

                val cycleStart = android.os.SystemClock.elapsedRealtime()
                try {
                    // Check network connectivity first
                    val netState = interfaceMonitor.state.value
                    if (!netState.isConnected) {
                        updateNotification("No network — waiting…")
                        diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "No network, skipping scan")
                        delay(5000)
                        continue
                    }

                    // Precision-Scale: Update Network Profile
                    repository.setNetworkProfile(netState.activeInterface?.ssid)


                    val isEff = credentialStore.isEfficiencyMode() && !isAppInForeground
                    updateNotification(
                        if (isEff) "Efficiency Protection Active"
                        else "Protected: ${netState.activeInterface?.ssid ?: "Securing Network"}…"
                    )

                    if (isEff && scanCount > 0 && !isFirstScan) {
                        // In efficiency mode, wait for external triggers (Sentinel) instead of ticking
                        // We do this by continuing the loop early if no trigger occurred
                        // However, we still scan once every 15 mins for baseline
                        val lastScanDiff = android.os.SystemClock.elapsedRealtime() - lastHistoryTimestamp
                        if (lastScanDiff < 15 * 60 * 1000L) {
                             delay(30_000L)
                             continue
                        }
                    }

                    // ── Multi-Scan Strategy ──
                    val isDeepScan = scanCount % 10 == 0 || isFirstScan
                    updateNotification(if(isDeepScan) "Deep Profiling..." else "Scan: ${netState.activeInterface?.ssid ?: "network"}...")
                    
                    val result = if (isDeepScan) {
                        isFirstScan = false
                        networkScanner.fullScan()
                    } else {
                        networkScanner.incrementalScan()
                    }
                    scanCount++

                    // mDNS enrichment
                    val mdnsDevices = try {
                        mdnsDiscovery.discoverDevices(if (result.scanDurationMs > 5000) 2000 else 1500)
                    } catch (e: Exception) {
                        diagnostics.error("mDNS failed", e)
                        emptyList()
                    }
                    val enriched = mdnsDiscovery.enrichDevicesWithMdns(result.devices, mdnsDevices)

                    // Behavior analysis
                    val traffic = repository.getRecentTrafficSnapshot()
                    val analyzed = behaviorEngine.analyzeDevices(enriched, traffic)

                    // ── Performance: Incremental Diff ──
                    val diff = diffEngine.computeDiff(analyzed)
                    
                    // Signal + pattern learning enrichment
                    val finalDevices = analyzed.map { device ->
                        val isChanged = device.mac in diff.changedMacs
                        
                        val actScore = when (device.activityLevel) {
                            com.wifimonitor.data.ActivityLevel.IDLE -> 0f
                            com.wifimonitor.data.ActivityLevel.LOW -> 0.2f
                            com.wifimonitor.data.ActivityLevel.BROWSING -> 0.5f
                            com.wifimonitor.data.ActivityLevel.ACTIVE -> 0.75f
                            com.wifimonitor.data.ActivityLevel.HEAVY -> 1f
                        }
                        signalProcessor.recordActivity(device.mac, actScore)
                        sessionTracker.updatePeakActivity(device.mac, actScore)
                        sessionTracker.recordLatency(device.mac, device.pingResponseMs)
                        sessionTracker.recordActivity(device.mac, actScore)
                        intelligenceEngine.detectStateTransitions(device.mac, device.activityLevel)
                        
                        val anomaly = patternLearner.isAnomalous(device.mac, actScore, device.pingResponseMs)
                        if (anomaly.isAnomalous) {
                            diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "Anomaly: ${device.mac}", anomaly.reason)
                            launch {
                                repository.addAlertRecord(com.wifimonitor.data.AlertRecord(
                                    type = com.wifimonitor.data.AlertType.ANOMALY,
                                    deviceMac = device.mac,
                                    message = "Anomaly on ${device.displayName}: ${anomaly.reason}"
                                ))
                            }
                        }

                        // --- Precision-Scale Sentinel Logic ---
                        
                        // 1. Identity Resolution (Auto-Naming)
                        val (suggested, confidence) = intelligenceEngine.resolveProbabilisticIdentity(device)
                        
                        // 2. Activity Fingerprinting
                        if (intelligenceEngine.isActivityFingerprintAnomalous(device)) {
                            launch {
                                repository.addAlertRecord(com.wifimonitor.data.AlertRecord(
                                    type = com.wifimonitor.data.AlertType.SUSPICIOUS_BEHAVIOR,
                                    deviceMac = device.mac,
                                    message = "Odd-hour Activity: ${device.displayName} is active during its usual sleep window (3 AM pattern)."
                                ))
                            }
                        }
                        
                        // 3. Port Change Detection
                        val existing = runBlocking { repository.getDeviceSnapshot(device.mac) }
                        if (existing != null && device.openPorts.isNotBlank() && existing.lastSeenPorts != device.openPorts) {
                            launch {
                                repository.addAlertRecord(com.wifimonitor.data.AlertRecord(
                                    type = com.wifimonitor.data.AlertType.PORT_CHANGE,
                                    deviceMac = device.mac,
                                    message = "Port Evolution: New services detected on ${device.displayName}"
                                ))
                                repository.updateLastSeenPorts(device.mac, device.openPorts)
                            }
                        }

                        // ── Level 7: High-Fidelity Diagnostics & Inference ──
                        if (isChanged && device.status == com.wifimonitor.data.DeviceStatus.ONLINE) {
                            val rcaDiagnosis = rcaAnalyzer.analyze(listOf(device))
                            val rcaNarrative = rcaAnalyzer.toNarrative(rcaDiagnosis)
                            if (rcaDiagnosis !is RootCauseAnalyzer.Diagnosis.Healthy) {
                                diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "RCA [${device.mac}]", rcaNarrative)
                            }
                            
                            val usagePattern = inferenceEngine.resolveUsagePattern(device.mac, device.totalSessions, device.activityLevel)
                            val reputation = reputationEngine.calculateReputation(device)
                            
                            device.copy(
                                behaviorLabel = "$usagePattern | ${reputation.label}",
                                suggestedName = suggested,
                                confidenceScore = confidence,
                                pingResponseMs = signalProcessor.smoothedLatency(device.mac).let { if (it > 0) it else device.pingResponseMs },
                                networkInterface = netState.activeInterface?.name ?: ""
                            )
                        } else {
                            device.copy(
                                suggestedName = suggested,
                                confidenceScore = confidence,
                                pingResponseMs = signalProcessor.smoothedLatency(device.mac).let { if (it > 0) it else device.pingResponseMs },
                                networkInterface = netState.activeInterface?.name ?: ""
                            )
                        }
                    }

                    // 4. Spoof Detection
                    val spoofedMacs = intelligenceEngine.checkSpoofing(finalDevices)
                    if (spoofedMacs.isNotEmpty()) {
                        spoofedMacs.forEach { mac ->
                            launch {
                                repository.addAlertRecord(AlertRecord(
                                    type = AlertType.ANOMALY,
                                    deviceMac = mac,
                                    message = "Network Conflict: Multiple devices sharing IP or MAC OUI mismatch (Potential Spoofing)."
                                ))
                            }
                        }
                    }


                    // ── Level 10: Shadow Device Detection ──
                    val arpTable = arpTableReader.readArpTable()
                    val finalDevicesWithShadow = finalDevices.map { device ->
                        val hasTraffic = traffic.any { it.deviceMac == device.mac }
                        val hasHostname = device.hostname.isNotBlank()
                        
                        if (!hasTraffic && !hasHostname && device.mac in arpTable.values.map { it.uppercase() }) {
                            // Suspiciously Silent
                            diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "Shadow Detection", device.mac)
                            device.copy(behaviorLabel = "Suspiciously Silent | ${device.behaviorLabel}")
                        } else {
                            device
                        }
                    }

                    // Persist
                    repository.updateScanResults(finalDevicesWithShadow)
                    intelligenceEngine.checkCorrelations(finalDevicesWithShadow)
                    
                    // ── Level 10: Behavioral Persistence ──
                    // (History recording is handled in DeviceRepository.updateScanResults)
                    
                    // ── Level 6: Engineering Intelligence ──
                    val currentSsid = netState.activeInterface?.ssid
                    if (currentSsid != lastSsid) {
                        isFirstScan = true
                        lastSsid = currentSsid
                        diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "Network Switched: $currentSsid")
                    }
                    
                    fingerprinter.updateFingerprint(currentSsid, finalDevices)
                    intelligenceEngine.detectBottleneck(finalDevices)
                    networkMemory.recordSeenDevices(currentSsid, finalDevices)
                    flowTracker.pruneOldFlows()
                    
                    // Silent device detection (Cross-check ARP macs)
                    val arpMacs = arpTable.values.map { it.uppercase() }.toSet()
                    intelligenceEngine.detectSilentDevices(currentSsid, finalDevices.size, arpMacs)

                    // ── Managed Mode Router Sync ──
                    if (credentialStore.isManagedMode()) {
                        syncRouterData()
                    }

                    val duration = android.os.SystemClock.elapsedRealtime() - cycleStart
                    updateNotification("${finalDevices.size} devices · ${duration}ms")
                    diagnostics.perf("Cycle: ${duration}ms, ${finalDevices.size} devices")

                    // Periodic maintenance
                    pruneTimer += duration + scanInterval
                    if (pruneTimer >= PRUNE_INTERVAL_MS) {
                        repository.pruneOldTraffic()
                        sessionTracker.pruneStale()
                        patternLearner.pruneStale()
                        pruneTimer = 0
                    }

                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    diagnostics.error("Scan cycle", e)
                    updateNotification("Monitoring (recovered)")
                }

                // Adaptive delay
                delay(scanInterval)
            }
        }
    }
    private fun startIspDiagnostics() {
        ispJob?.cancel()
        ispJob = scope.launch {
            while (isActive) {
                if (!credentialStore.isEfficiencyMode()) {
                    val status = ispDiagnosticEngine.probeIspHealth()
                    if (!status.isBackboneHealthy) {
                        diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "ISP ALERT", status.statusMessage)
                    }
                    delay(120_000L) 
                } else {
                    delay(30 * 60 * 1000L) // 30 mins in efficiency mode
                }
            }
        }
    }

    private fun startArpSentinel() {
        sentinelJob?.cancel()
        sentinelJob = scope.launch {
            val knownMacs = mutableSetOf<String>()
            
            while (isActive) {
                try {
                    // Scan ARP Table (Sentinel Mode)
                    val currentTable = arpTableReader.readArpTable()
                    val arrivals = currentTable.values.filter { it !in knownMacs && it != "00:00:00:00:00:00" }
                    
                        if (arrivals.isNotEmpty()) {
                            arrivals.forEach { mac ->
                                diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "Sentinel: Device Joined", mac)
                                sessionTracker.markOnline(mac)
                                knownMacs.add(mac)
                            }
                            // Signal an immediate scan without killing the active job
                            scanTrigger.tryEmit(Unit)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Sentinel error: ${e.message}")
                }
                delay(2000) // High-frequency polling
            }
        }
    }

    private fun startDnsMonitor() {
        dnsJob?.cancel()
        dnsJob = scope.launch {
            if (credentialStore.isEfficiencyMode()) return@launch
            try {
                dnsMonitor.startCapture { query ->
                    diagnostics.dnsCapture(query.domain, query.sourceIp)
                    launch {
                        try {
                            repository.addTrafficRecord(TrafficRecord(
                                deviceMac = query.sourceIp,
                                domain = query.domain,
                                timestamp = query.timestamp,
                                source = "mDNS"
                            ))
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { diagnostics.error("DNS monitor", e) }
        }
    }

    private suspend fun addAlertStub(alert: AlertRecord) {
        try {
            repository.addAlertRecord(alert)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannel(CHANNEL_ID, "WiFi Intelligence", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Background network monitoring"; nm.createNotificationChannel(this)
        }
        NotificationChannel(CHANNEL_ALERT_ID, "Network Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts for new devices and anomalies"; nm.createNotificationChannel(this)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Domination Suite")
            .setContentText(text)
            .setSubText("Network Intelligence Active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }

    private suspend fun syncRouterData() {
        // Fetch DNS logs
        val dnsResult = routerApiClient.fetchDnsLogs()
        if (dnsResult is com.wifimonitor.network.RouterApiClient.RouterResult.Success) {
            dnsResult.data.forEach { log ->
                sessionTracker.markDomain(log.clientIp, log.domain) // Simplified: using IP as temp ID or resolver mapping
                repository.addTrafficRecord(TrafficRecord(
                    deviceMac = log.clientIp, // Mapping IP to MAC usually happens in repo/tracker
                    domain = log.domain,
                    timestamp = log.timestamp,
                    source = "Router"
                ))
            }
        }

        // Fetch Bandwidth stats
        val bwResult = routerApiClient.getBandwidthStats()
        if (bwResult is com.wifimonitor.network.RouterApiClient.RouterResult.Success) {
            bwResult.data.forEach { (iface, bytes) ->
                // Map interface/IP to device if possible
                // For now, update global total or per-device if router provides it
            }
        }
    }

    private fun handleGatewayMode() {
        if (credentialStore.isGatewayMode()) {
            Log.i(TAG, "Gateway Mode Enabled — Starting Infrastructure")
            // Start servers on high ports (Audit 6: Root-Free Compatibility)
            dnsServer.start(5354)
            proxyServer.start(8080)
            walledGarden.start(8081) // Access Denied dashboard
            
            // Apply Router Redirection (Audit 3: Verified Execution)
            scope.launch {
                val activeIface = interfaceMonitor.state.value.activeInterface ?: return@launch
                val phoneIp = activeIface.ip
                
                // Audit 6: Pass high ports for non-root redirection
                val dnsRes = routerApiClient.enableTransparentDnsRedirection(phoneIp, 5354)
                val proxyRes = routerApiClient.enableTransparentProxyRedirection(phoneIp, 8080)
                
                if (dnsRes is com.wifimonitor.network.RouterApiClient.RouterResult.Success && 
                    proxyRes is com.wifimonitor.network.RouterApiClient.RouterResult.Success) {
                    diagnostics.log(DiagnosticLogger.DiagEntry.Category.STATE, "Gateway Mode: ACTIVE & VERIFIED")
                } else {
                    diagnostics.error("Gateway Mode: Redirection failed", null)
                }
            }
        } else {
            dnsServer.stop()
            proxyServer.stop()
            // Router cleanup will be handled on next sync or manual refresh
        }
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startTrafficAuditVpn() {
        val intent = Intent(this, TrafficAuditVpnService::class.java).apply {
            action = TrafficAuditVpnService.ACTION_START
        }
        startService(intent)
        Log.i(TAG, "Traffic Audit VPN Requested")
    }

    private fun stopTrafficAuditVpn() {
        val intent = Intent(this, TrafficAuditVpnService::class.java).apply {
            action = TrafficAuditVpnService.ACTION_STOP
        }
        startService(intent)
    }
}
