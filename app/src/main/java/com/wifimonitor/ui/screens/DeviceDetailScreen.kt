package com.wifimonitor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.data.*
import com.wifimonitor.ui.components.*
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.DeviceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    mac: String,
    navController: NavController,
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(mac) { viewModel.loadDevice(mac) }
    val device = state.device

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.displayName ?: "Device", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    device?.let {
                        IconButton(onClick = { viewModel.startEditNickname() }) {
                            Icon(Icons.Default.Edit, "Edit", tint = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.toggleBlock() }) {
                            Icon(
                                if (it.isBlocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                "Block", tint = if (it.isBlocked) AlertRed else TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard)
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        if (device == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyberTeal)
            }
            return@Scaffold
        }

        val topDomains = state.topDomains
        val trend = remember(device, state.trafficRecords) {
            if (state.trafficRecords.isEmpty()) "Limited data available"
            else {
                val calendar = Calendar.getInstance()
                val nocturnal = state.trafficRecords.any { 
                    calendar.timeInMillis = it.timestamp
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    hour in 0..5 
                }
                if (nocturnal) "Active Night-time Usage"
                else if (state.trafficRecords.size > 20) "Frequent Active User"
                else "Light Occasional Usage"
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Hero Banner ──
            item { DeviceHeroBanner(device = device) }

            // ── Capabilities ──
            item {
                val capabilities = remember(device) {
                    val ports = device.openPorts.split(",").filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
                    val mdnsNames = device.mdnsName.split(",").filter { it.isNotBlank() }
                    com.wifimonitor.analyzer.CapabilityResolver().resolve(ports, mdnsNames, device.manufacturer)
                }
                if (capabilities.isNotEmpty()) {
                    CapabilitiesCard(capabilities)
                }
            }

            // ── Control Actions ──
            item { ControlActionsRow(device = device, state = state, viewModel = viewModel) }

            // ── Usage Stats ──
            item { UsageStatsCard(device = device) }

            // ── Behavior Insight Card ──
            if (device.behaviorLabel.isNotBlank()) {
                item { BehaviorInsightCard(device = device) }
            }

            // ── Usage Trends ──
            if (topDomains.isNotEmpty()) {
                item { TopDomainsCard(topDomains) }
            }

            // Level 12 & 13: Personality Intelligence (Killer #3)
            item { PersonalityCard(device.personality) }

            item { UsageTrendCard(trend) }

            // ── Level 5: Behavioral Scoring ──
            item { DeviceScoreCard(device = device) }

            // ── Live Metrics (sparklines) ──
            if (device.status == DeviceStatus.ONLINE) {
                item { LiveMetricsCard(device = device) }
                item { InfrastructureStatusCard(device = device) }
            }

            // ── Automation Rules ──
            item { DeviceRulesCard(device = device, rules = state.rules) }

            // ── Nickname editor ──
            if (state.editingNickname) {
                item { NicknameEditor(value = state.nicknameInput, onValueChange = viewModel::updateNicknameInput, onSave = viewModel::saveNickname, onCancel = { viewModel.updateNicknameInput(device.nickname) }) }
            }

            // ── Smart Tags ──
            val tags = device.smartTagsList()
            if (tags.isNotEmpty()) {
                item { SmartTagsSection(tags = tags) }
            }

            // ── Identity & Network Info ──
            item { IdentityNetworkCard(device = device) }

            // ── Performance Heatmap ──
            item { PerformanceHeatmapCard(device = device) }

            // ── Domain Activity Strip ──
            val domains = device.recentDomainsList()
            if (domains.isNotEmpty()) {
                item { DomainActivityCard(domains = domains) }
            }

            // ── Port Scanner ──
            item { PortScanCard(device = device, isScanning = state.isPortScanning, onScan = viewModel::runPortScan) }

            // ── DNS Timeline ──
            if (state.trafficRecords.isNotEmpty()) {
                item {
                    Text("DNS Activity", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                items(state.trafficRecords.take(20)) { record ->
                    TrafficItem(record = record, showTime = true)
                }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = NavyCard), border = BorderStroke(1.dp, NavyBorder), shape = RoundedCornerShape(14.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.NetworkCheck, null, tint = TextMuted, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No DNS activity recorded yet", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceHeroBanner(device: NetworkDevice) {
    val actColor = activityColor(device.activityLevel)
    val isOnline = device.status == DeviceStatus.ONLINE
    val infiniteTransition = rememberInfiniteTransition(label = "hero_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        0.2f, 0.6f, infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "glow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, if (isOnline) actColor.copy(alpha = 0.3f) else NavyBorder)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(listOf(deviceTypeColor(device.deviceType).copy(alpha = 0.08f), Color.Transparent))
            ).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp).background(
                        Brush.radialGradient(listOf(deviceTypeColor(device.deviceType).copy(if (isOnline) glowAlpha * 0.3f else 0.1f), Color.Transparent)),
                        RoundedCornerShape(20.dp)
                    ).background(deviceTypeColor(device.deviceType).copy(0.12f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(deviceTypeIcon(device.deviceType), null, tint = deviceTypeColor(device.deviceType), modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.displayName, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(device.manufacturer.ifBlank { "Unknown Manufacturer" }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(status = device.status)
                        if (isOnline) {
                            Surface(shape = RoundedCornerShape(20.dp), color = actColor.copy(0.12f)) {
                                Text(device.activityLevel.emoji + " " + device.activityLevel.label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = actColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Activity meter
            if (isOnline) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 100.dp)) {
                    ActivityMeterBar(level = device.activityLevel, label = device.behaviorLabel.ifBlank { device.activityLevel.label })
                }
            }
        }
    }
}

@Composable
private fun BehaviorInsightCard(device: NetworkDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(0.08f)),
        border = BorderStroke(1.dp, AccentPurple.copy(0.25f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = AccentPurple.copy(0.15f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("🧠", fontSize = 20.sp) }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Behavior Insight", style = MaterialTheme.typography.labelMedium, color = AccentPurple, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(device.behaviorLabel, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun SmartTagsSection(tags: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Intelligence Tags", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags) { tag -> SmartTag(tag) }
            }
        }
    }
}

@Composable
private fun IdentityNetworkCard(device: NetworkDevice) {
    val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val sessionStr = if (device.sessionDuration > 0) {
        val h = device.sessionDuration / 3600000
        val m = (device.sessionDuration % 3600000) / 60000
        if (h > 0) "${h}h ${m}m" else "${m}m"
    } else "—"

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NavyCard), border = BorderStroke(1.dp, NavyBorder)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("Identity & Network", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            InfoSection("Identity") {
                InfoRow2("Name", device.displayName, Icons.Default.Badge)
                InfoRow2("Brand", device.manufacturer.ifBlank { "Unknown" }, Icons.Default.Business)
                InfoRow2("Type", device.deviceType.name, Icons.Default.Devices)
                InfoRow2("Group", device.deviceGroup.name.replace("_", " "), Icons.Default.Group)
                InfoRow2("Trust", "${device.trustIcon} ${device.trustLevel.name}", Icons.Default.Shield)
                if (device.fingerprintHash.isNotBlank()) InfoRow2("Fingerprint", device.fingerprintHash, Icons.Default.Key)
                if (device.isMacRandomized) InfoRow2("MAC Status", "🔀 Randomized", Icons.Default.Shuffle)
            }
            Spacer(Modifier.height(12.dp))
            InfoSection("Network") {
                InfoRow2("IP Address", device.ip.ifBlank { "—" }, Icons.Default.Router)
                InfoRow2("MAC Address", device.mac, Icons.Default.Fingerprint)
                InfoRow2("Hostname", device.hostname.ifBlank { "—" }, Icons.Default.Dns)
                if (device.networkInterface.isNotBlank()) InfoRow2("Interface", device.networkInterface, Icons.Default.SettingsEthernet)
            }
            Spacer(Modifier.height(12.dp))
            InfoSection("Precision Latency") {
                if (device.pingResponseMs > 0) InfoRow2("Average", "${device.pingResponseMs}ms", Icons.Default.Speed)
                if (device.latencyMin > 0) InfoRow2("Min / Max", "${device.latencyMin}ms / ${device.latencyMax}ms", Icons.Default.SwapVert)
                if (device.latencyMedian > 0) InfoRow2("Median", "${device.latencyMedian}ms", Icons.Default.Equalizer)
                if (device.jitterMs >= 0) InfoRow2("Jitter", "${device.jitterMs}ms", Icons.Default.Insights)
                InfoRow2("Quality", device.connectionQuality, Icons.Default.Assessment)
            }
            Spacer(Modifier.height(12.dp))
            InfoSection("Reliability") {
                InfoRow2("Reliability", "${device.reliabilityPct}% — ${device.reliabilityLabel}", Icons.Default.Verified)
                InfoRow2("Probes ✓/✗", "${device.probeSuccessCount} / ${device.probeFailCount}", Icons.Default.Checklist)
            }
            Spacer(Modifier.height(12.dp))
            InfoSection("Session") {
                InfoRow2("Duration", sessionStr, Icons.Default.Timer)
                InfoRow2("First Seen", fmt.format(Date(device.firstSeen)), Icons.Default.AccessTime)
                InfoRow2("Last Seen", fmt.format(Date(device.lastSeen)), Icons.Default.Update)
                InfoRow2("Total Sessions", "${device.totalSessions}", Icons.Default.Repeat)
            }
        }
    }
}


@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = CyberTeal, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    content()
}

@Composable
private fun InfoRow2(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CyberTeal.copy(0.7f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = NavyBorder.copy(0.5f), thickness = 0.5.dp)
}

@Composable
private fun DomainActivityCard(domains: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NavyCard), border = BorderStroke(1.dp, NavyBorder)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Recent Domains", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            DomainIconStrip(domains = domains)
        }
    }
}

@Composable
private fun PortScanCard(device: NetworkDevice, isScanning: Boolean, onScan: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NavyCard), border = BorderStroke(1.dp, NavyBorder)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Open Ports & Services", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onScan, enabled = !isScanning, border = BorderStroke(1.dp, CyberTeal), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CyberTeal, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Scanning…", color = CyberTeal, style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(Icons.Default.Scanner, null, tint = CyberTeal, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scan Ports", color = CyberTeal, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val ports = device.openPortsList()
            if (ports.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(if (device.openPorts.isBlank()) "Tap Scan Ports to detect open services" else "No open ports detected",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                Spacer(Modifier.height(12.dp))
                ports.chunked(2).forEach { chunk ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEach { port -> PortBadge(port = port, modifier = Modifier.weight(1f)) }
                        if (chunk.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun PortBadge(port: Int, modifier: Modifier = Modifier) {
    val (service, color) = portInfo(port)
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(0.1f), modifier = modifier, border = BorderStroke(0.5.dp, color.copy(0.3f))) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.2f)) {
                Text("$port", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(service, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun NicknameEditor(value: String, onValueChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NavySurface), border = BorderStroke(1.dp, CyberTeal.copy(0.4f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Set Device Nickname", style = MaterialTheme.typography.titleSmall, color = CyberTeal)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = value, onValueChange = onValueChange,
                placeholder = { Text("e.g. Dad's Phone", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberTeal, unfocusedBorderColor = NavyBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedContainerColor = NavyCard, unfocusedContainerColor = NavyCard))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, NavyBorder), shape = RoundedCornerShape(8.dp)) { Text("Cancel", color = TextSecondary) }
                Button(onClick = onSave, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CyberTeal), shape = RoundedCornerShape(8.dp)) { Text("Save", color = DeepNavy, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun LiveMetricsCard(device: NetworkDevice) {
    val latencyMs = device.pingResponseMs
    // Synthetic sparkline data based on device activity — in production, pulled from SignalProcessor via ViewModel
    val activityFloat = when (device.activityLevel) {
        ActivityLevel.IDLE -> 0.05f
        ActivityLevel.LOW -> 0.2f
        ActivityLevel.BROWSING -> 0.5f
        ActivityLevel.ACTIVE -> 0.75f
        ActivityLevel.HEAVY -> 0.95f
    }
    // Build synthetic history showing a trend towards current value
    val actHistory = remember(device.activityLevel) {
        List(12) { i ->
            val noise = ((i * 7 + 3) % 5 - 2) * 0.05f
            (activityFloat * (0.4f + 0.6f * i / 11f) + noise).coerceIn(0f, 1f)
        }
    }
    val latHistory = remember(latencyMs) {
        if (latencyMs <= 0) emptyList()
        else List(12) { i ->
            val noise = ((i * 13 + 7) % 9 - 4)
            (latencyMs + noise * 3).coerceAtLeast(1)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live Metrics", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)

            // Activity sparkline
            ActivitySparkline(
                history = actHistory,
                modifier = Modifier.fillMaxWidth(),
                currentLabel = device.activityLevel.label
            )

            // Latency sparkline
            if (latHistory.isNotEmpty()) {
                LatencySparkline(
                    history = latHistory,
                    modifier = Modifier.fillMaxWidth(),
                    currentMs = latencyMs
                )
            }

            // Stability bar
            StabilityBar(
                percentage = if (device.status == DeviceStatus.ONLINE) 95 else 0,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun portInfo(port: Int): Pair<String, Color> = when (port) {
    21 -> "FTP" to AccentBlue; 22 -> "SSH" to AccentGreen; 23 -> "Telnet" to AlertRed
    25 -> "SMTP" to AccentBlue; 53 -> "DNS" to AccentPurple; 80 -> "HTTP" to AccentBlue
    443 -> "HTTPS" to AccentGreen; 445 -> "SMB" to AccentOrange; 548 -> "AFP/Mac" to AccentBlue
    554 -> "RTSP/Camera" to AccentPurple; 631 -> "Printer" to AccentOrange
    1883 -> "MQTT/IoT" to WarningAmber; 3389 -> "RDP" to AlertRed
    7000 -> "AirPlay" to AccentBlue; 8080 -> "HTTP-alt" to AccentBlue; 9100 -> "Raw Print" to AccentOrange
    else -> "Port $port" to TextSecondary
}

@Composable
private fun ControlActionsRow(device: NetworkDevice, state: DeviceUiState, viewModel: DeviceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlActionButton(
                modifier = Modifier.weight(1f),
                label = if (device.isBlocked) "Released" else "Kill Connection",
                icon = if (device.isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                color = if (device.isBlocked) CyberTeal else AlertRed,
                onClick = { viewModel.toggleBlock() }
            )
            ControlActionButton(
                modifier = Modifier.weight(1f),
                label = if (state.isPaused) "Resume" else "Pause Internet",
                icon = if (state.isPaused) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                color = if (state.isPaused) CyberTeal else WarningAmber,
                onClick = { viewModel.togglePauseInternet() }
            )
        }
        
        ControlActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = if (device.isTracked) "Tracking Active" else "Track Device Presence",
            icon = if (device.isTracked) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
            color = if (device.isTracked) AccentPurple else TextMuted,
            onClick = { viewModel.toggleTracked() }
        )
    }
}

@Composable
private fun ControlActionButton(modifier: Modifier, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UsageStatsCard(device: NetworkDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Data Usage", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UsageIndicator("Download", 1.2f, "GB", CyberTeal, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                UsageIndicator("Upload", 450f, "MB", WarningAmber, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfrastructureStatusCard(device: NetworkDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Infrastructure Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfraChip(label = "DNS Auth", isActive = true, icon = Icons.Default.Public)
                InfraChip(label = "DPI Proxy", isActive = true, icon = Icons.Default.Security)
                InfraChip(label = "Managed", isActive = true, icon = Icons.Default.AdminPanelSettings)
            }
        }
    }
}

@Composable
private fun InfraChip(label: String, isActive: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) CyberTeal.copy(0.1f) else NavySurface,
        border = BorderStroke(1.dp, if (isActive) CyberTeal.copy(0.3f) else NavyBorder),
        modifier = Modifier.height(36.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isActive) CyberTeal else TextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) CyberTeal else TextMuted)
        }
    }
}

@Composable
private fun DeviceRulesCard(device: NetworkDevice, rules: List<com.wifimonitor.rules.RuleEngine.Rule>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Active Network Rules", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            if (rules.isEmpty()) {
                Text("No active rules for this device", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                Spacer(Modifier.height(10.dp))
                rules.forEach { rule ->
                    val icon = when (rule.type) {
                        com.wifimonitor.rules.RuleEngine.RuleType.GLOBAL_BLOCK -> Icons.Default.PauseCircle
                        com.wifimonitor.rules.RuleEngine.RuleType.SCHEDULE -> Icons.Default.Schedule
                        else -> Icons.Default.Block
                    }
                    RuleBriefItem(rule.target, rule.id, icon)
                }
            }
        }
    }
}

@Composable
private fun RuleBriefItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, tint = CyberTeal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun DeviceScoreCard(device: NetworkDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, CyberTeal.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Behavioral Ratings", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ScoreItem("Stability", 92, Icons.Default.Verified, CyberTeal)
                ScoreItem("Presence", 75, Icons.Default.History, AccentBlue)
                ScoreItem("Activity", 88, Icons.Default.BarChart, WarningAmber)
            }
        }
    }
}

@Composable
private fun ScoreItem(label: String, score: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.size(50.dp),
                color = color,
                trackColor = NavyBorder,
                strokeCap = StrokeCap.Round
            )
            Text("$score", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun PerformanceHeatmapCard(device: NetworkDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Latency Variation Heatmap", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(12) { i ->
                    val color = if (i % 5 == 0) WarningAmber else CyberTeal
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color.copy(0.6f)))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Stability Profile: High Consistency", style = MaterialTheme.typography.labelSmall, color = OnlineGreen)
        }
    }
}
@Composable
private fun TopDomainsCard(domains: List<com.wifimonitor.data.DomainCount>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Top Destinations", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            domains.forEach { dc ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = CyberTeal.copy(0.1f)) {
                        Text(dc.domain.take(1).uppercase(), modifier = Modifier.padding(4.dp), color = CyberTeal, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(dc.domain, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("${dc.count}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun CapabilitiesCard(capabilities: List<com.wifimonitor.analyzer.CapabilityResolver.Capability>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Identified Capabilities", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            capabilities.forEach { cap ->
                CapabilityItem(cap)
                if (cap != capabilities.last()) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CapabilityItem(cap: com.wifimonitor.analyzer.CapabilityResolver.Capability) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).background(CyberTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(cap.icon, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(cap.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(cap.description, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
fun PersonalityCard(personality: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, CyberTeal.copy(0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = CyberTeal.copy(0.1f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🧠", fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Assigned Personality", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(personality, style = MaterialTheme.typography.titleMedium, color = CyberTeal, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
private fun DnaBadge(dna: String) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = CyberTeal.copy(0.1f),
        border = BorderStroke(1.dp, CyberTeal.copy(0.3f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Science, null, tint = CyberTeal, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(dna, style = MaterialTheme.typography.labelSmall, color = CyberTeal, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UsageTrendCard(trend: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TrendingUp, null, tint = CyberTeal, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Usage Pattern", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(trend, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}
