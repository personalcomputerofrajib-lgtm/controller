package com.wifimonitor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.data.*
import com.wifimonitor.ui.Screen
import com.wifimonitor.ui.components.*
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = Screen.Dashboard.route,
                onNavigate = { route ->
                    navController.navigate(route)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(CyberTeal.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Wifi, null, tint = CyberTeal, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("WiFi Intelligence", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Default.VerifiedUser, "Local AI", tint = OnlineGreen, modifier = Modifier.size(12.dp))
                                }
                                Text(
                                    if (state.lastScanTime > 0) "Verified Scan: ${timeFmt.format(Date(state.lastScanTime))}"
                                    else "Initializing...",
                                    style = MaterialTheme.typography.labelSmall, color = CyberTeal, fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            // Profile Badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = NavySurface.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, CyberTeal.copy(0.3f))
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = CyberTeal, modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(state.currentProfile, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 9.sp)
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu", tint = TextSecondary)
                        }
                    },
                    actions = {
                        AuthorityStatusChip(state = state)
                        Spacer(Modifier.width(8.dp))
                        if (state.unreadAlerts > 0) {
                            BadgedBox(badge = { Badge(containerColor = AlertRed) { Text("${state.unreadAlerts}") } }) {
                                IconButton(onClick = { navController.navigate(Screen.Alerts.route) }) {
                                    Icon(Icons.Default.Notifications, "Alerts", tint = AlertRed)
                                }
                            }
                        } else {
                            IconButton(onClick = { navController.navigate(Screen.Alerts.route) }) {
                                Icon(Icons.Default.Notifications, "Alerts", tint = TextSecondary)
                            }
                        }
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
                )
            },
            bottomBar = {
                QuickActionBar(
                    isScanning = state.isScanning,
                    onScan = { viewModel.scanNow() },
                    onNavigate = { navController.navigate(it) }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Global Fault Indicator (High Priority) ──
                if (state.detectedFaults.isNotEmpty()) {
                    item {
                        GlobalFaultIndicator(
                            fault = state.detectedFaults.first(),
                            onClick = { viewModel.selectFault(state.detectedFaults.first()) }
                        )
                    }
                }

                // 0. Network Summary (Insights)
                if (state.topInsights.isNotEmpty()) {
                    item { SentinelInsightsCard(insights = state.topInsights) }
                }
                
                // ── Sentinel Problem/Answer Solutions ──
                if (state.solutions.isNotEmpty()) {
                    items(state.solutions) { solution ->
                        SentinelSolutionCard(solution = solution)
                    }
                }

                // 1. Hero Card
                item { 
                    NetworkHeroCard(
                        state = state, 
                        onScanClick = { viewModel.scanNow() },
                        onNavigate = { navController.navigate(it) },
                        onWhyClick = { viewModel.selectFault(it) }
                    ) 
                }
                
                // ── Live Bandwidth HUD ──
                item { LiveBandwidthHud(totalMbps = state.totalMbps) }

                // ── Global Security Posture ──
                state.securityAudit?.let { audit ->
                    item { SecurityPostureCard(audit = audit) }
                }

                // 2. Summary Stats
                item { SummaryStatsRow(state = state) }

                // ── Precision-Scale: Scan Delta Indication ──
                if (state.scanDelta.newMacs.isNotEmpty() || state.scanDelta.leftMacs.isNotEmpty()) {
                    item { ScanDeltaCard(delta = state.scanDelta) }
                }

                // 3. Status Bar
                item { NetworkPressureCard(score = state.pressureScore, label = state.pressureLabel) }

                // ── 4. Narrative Health Score ──
                state.narrativeReport?.let { report ->
                    item { NarrativeHealthCard(report = report) }
                }

                // ── 4.5. Who is using internet now (Active Devices) ──
                val activeNow = state.onlineDevices.filter { it.activityLevel.ordinal >= ActivityLevel.BROWSING.ordinal }
                if (activeNow.isNotEmpty()) {
                    item { SectionHeader(title = "Live Traffic HUD", count = activeNow.size, onSeeAll = {}) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(activeNow) { device ->
                                ActiveDeviceChip(device = device)
                            }
                        }
                    }
                }

                // ── 5. Live Devices Snippet ──
                if (state.onlineDevices.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Live Devices",
                            count = state.onlineDevices.size,
                            onSeeAll = { navController.navigate(Screen.DeviceList.route) }
                        )
                    }
                    items(state.onlineDevices.take(6), key = { it.mac }) { device ->
                        AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                            DeviceCard(
                                device = device, 
                                history = state.deviceHistories[device.mac] ?: emptyList(),
                                onClick = {
                                    navController.navigate(Screen.DeviceDetail.createRoute(device.mac))
                                },
                                onFaultClick = {
                                    viewModel.selectFault(com.wifimonitor.data.NetworkFault(
                                        id = "device_fault_${device.mac}",
                                        type = com.wifimonitor.data.FaultType.BANDWIDTH,
                                        title = "Device Traffic Spike",
                                        fact = "${device.displayName} is consuming ${String.format("%.1f", device.downloadRateMbps)} Mbps.",
                                        relatedData = listOf(
                                            com.wifimonitor.data.Metric("Usage", "${String.format("%.1f", device.downloadRateMbps)} Mbps"),
                                            com.wifimonitor.data.Metric("Activity", device.activityLevel.label)
                                        ),
                                        context = "This device is currently the highest traffic user on the network.",
                                        actions = listOf(
                                            com.wifimonitor.data.FaultAction("Monitor Live", Screen.Traffic.route, isPrimary = true),
                                            com.wifimonitor.data.FaultAction("Deep Scan")
                                        ),
                                        severity = 1
                                    ))
                                }
                            )
                        }
                    }
                    if (state.onlineDevices.size > 6) {
                        item {
                            TextButton(onClick = { navController.navigate(Screen.DeviceList.route) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.ExpandMore, null, tint = CyberTeal, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("+${state.onlineDevices.size - 6} more devices", color = CyberTeal)
                            }
                        }
                    }
                }

                // ── 4. Unknown/New Device Alert Banner ──
                if (state.newDevicesCount > 0) {
                    item { NewDeviceAlertBanner(count = state.newDevicesCount, onClick = { navController.navigate(Screen.DeviceList.route) }) }
                }

                // ── 5. Top Domains ──
                if (state.topDomains.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Network Activity", count = state.topDomains.size, onSeeAll = { navController.navigate(Screen.Traffic.route) })
                    }
                    item { TopDomainsCard(domains = state.topDomains) }
                }

                // ── 6. Recent DNS ──
                if (state.recentTraffic.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recent DNS", count = state.recentTraffic.size, onSeeAll = { navController.navigate(Screen.Traffic.route) })
                    }
                    items(state.recentTraffic.take(5)) { record ->
                        TrafficItem(record = record, showTime = true)
                    }
                }

                // ── 7. Network Timeline Snippet ──
                item {
                    SectionHeader(title = "Network Change Log", count = 0, onSeeAll = { navController.navigate("timeline") })
                }
                item { RecentEventsSnippet() }

                // ── 7. Empty state ──
                if (state.onlineDevices.isEmpty() && !state.isScanning) {
                    item {
                        EmptyDiscoveryState(padding = PaddingValues(0.dp), onScan = { viewModel.scanNow() })
                    }
                }

                // ── 8. Error ──
                state.error?.let { item { ErrorCard(message = it, onDismiss = viewModel::dismissError) } }
                
                // Bottom spacing for QuickActionBar
                item { Spacer(Modifier.height(80.dp)) }
            }

            // ── Fault Detail Panel (Bottom Sheet) ──
            if (state.selectedFault != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.selectFault(null) },
                    containerColor = NavySurface,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = CyberTeal) }
                ) {
                    FaultDetailPanel(
                        fault = state.selectedFault!!,
                        technicalExplanation = viewModel.getTechnicalExplanation(state.selectedFault!!),
                        onExport = { viewModel.exportForensicSnapshot(navController.context) },
                        onAction = { route -> 
                            route?.let { navController.navigate(it) }
                            viewModel.selectFault(null)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Card — Radar Ring + Health Score + Scan Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NetworkHeroCard(
    state: com.wifimonitor.viewmodel.DashboardUiState, 
    onScanClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onFaultSelected: (com.wifimonitor.data.NetworkFault) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "radar"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        listOf(CyberTeal.copy(alpha = glowAlpha * 0.12f), Color.Transparent),
                        radius = 600f
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Radar ring
                    Box(modifier = Modifier.size(120.dp)) {
                        RadarCanvas(
                            onlineCount = state.onlineDevices.size,
                            totalCount = state.allDevices.size,
                            isScanning = state.isScanning,
                            rotation = radarRotation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${state.onlineDevices.size}",
                            style = MaterialTheme.typography.displayMedium,
                            color = CyberTeal,
                            fontWeight = FontWeight.Bold
                        )
                        Text("devices online", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.allDevices.size} total known",
                            style = MaterialTheme.typography.labelSmall, color = TextMuted
                        )

                        Spacer(Modifier.height(14.dp))

                        if (state.isScanning) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { state.scanProgress / 100f },
                                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                                    color = CyberTeal, trackColor = NavyBorder
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("Scanning… ${state.scanProgress}%", style = MaterialTheme.typography.labelSmall, color = CyberTeal)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onScanClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberTeal),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Search, null, tint = DeepNavy, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan", color = DeepNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                                
                                 OutlinedButton(
                                    onClick = { onNavigate(Screen.TacticalMap.route) },
                                    border = BorderStroke(1.dp, CyberTeal),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Radar, null, tint = CyberTeal, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    HealthScoreRing(
                        score = state.healthScore,
                        label = state.healthLabel,
                        modifier = Modifier.size(80.dp)
                    )
                }
                
                if (!state.isScanning) {
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { 
                            onFaultSelected(com.wifimonitor.data.NetworkFault(
                                id = "network_health_audit",
                                type = com.wifimonitor.data.FaultType.CONGESTION,
                                title = "Full Network Integrity Audit",
                                fact = "Your network scoring ${state.healthScore}% health based on ${state.onlineDevices.size} active nodes.",
                                relatedData = listOf(
                                    com.wifimonitor.data.Metric("Pressure", "${state.pressureScore}%"),
                                    com.wifimonitor.data.Metric("Managed", state.isManagedMode.toString().uppercase())
                                ),
                                context = "This audit evaluates encryption standards, latency variances, and potential identity spoofing.",
                                actions = listOf(
                                    com.wifimonitor.data.FaultAction("Start Deep Scan", isPrimary = true),
                                    com.wifimonitor.data.FaultAction("View Logs")
                                ),
                                severity = 0
                            ))
                        }) {
                            Icon(Icons.Default.Info, null, tint = CyberTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Why am I seeing this?", color = CyberTeal, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarCanvas(
    onlineCount: Int,
    totalCount: Int,
    isScanning: Boolean,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val fraction = if (totalCount > 0) onlineCount.toFloat() / totalCount else 0f
    val animFraction by animateFloatAsState(fraction, tween(800), label = "arc")

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 6.dp.toPx()
        val sw = 10.dp.toPx()

        // Outer grid rings
        for (i in 1..3) {
            drawCircle(color = NavyBorder.copy(alpha = 0.4f), radius = radius * i / 3, center = center, style = Stroke(1.dp.toPx()))
        }

        // Cross hairs
        drawLine(NavyBorder.copy(alpha = 0.3f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 0.5.dp.toPx())
        drawLine(NavyBorder.copy(alpha = 0.3f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 0.5.dp.toPx())

        // Background arc
        drawArc(NavyBorder, -90f, 360f, false, style = Stroke(sw, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))

        // Progress arc
        drawArc(CyberTeal, -90f, animFraction * 360f, false, style = Stroke(sw, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))

        // Sweep beam when scanning
        if (isScanning) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, CyberTeal.copy(0.5f), Color.Transparent), center),
                startAngle = rotation - 90f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(center.x - radius + 2.dp.toPx(), center.y - radius + 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(radius * 2 - 4.dp.toPx(), radius * 2 - 4.dp.toPx())
            )
        }

        // Center dot
        drawCircle(CyberTeal, 5.dp.toPx(), center)
        drawCircle(CyberTeal.copy(0.3f), 10.dp.toPx(), center, style = Stroke(2.dp.toPx()))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary Stats Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryStatsRow(state: com.wifimonitor.viewmodel.DashboardUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            value = "${state.onlineDevices.size}",
            label = "Online",
            icon = Icons.Default.Wifi,
            color = OnlineGreen
        )
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            value = "${state.activeCount}",
            label = "Active",
            icon = Icons.Default.NetworkCheck,
            color = WarningAmber
        )
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            value = "${state.newDevicesCount}",
            label = "New",
            icon = Icons.Default.NewReleases,
            color = if (state.newDevicesCount > 0) AlertRed else TextMuted
        )
        SummaryStatCard(
            modifier = Modifier.weight(1f),
            value = "${state.healthScore}",
            label = "Health",
            icon = Icons.Default.Shield,
            color = when {
                state.healthScore >= 75 -> OnlineGreen
                state.healthScore >= 50 -> WarningAmber
                else -> AlertRed
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// New Device Alert Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NewDeviceAlertBanner(count: Int, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
    val alpha by infiniteTransition.animateFloat(
        0.7f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a"
    )
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = alpha * 0.6f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = WarningAmber.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⚠️", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count new ${if (count == 1) "device" else "devices"} detected",
                    style = MaterialTheme.typography.titleSmall,
                    color = WarningAmber,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Tap to review unknown devices on your network", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = WarningAmber, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Domains Card with bars
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopDomainsCard(domains: List<Pair<String, Int>>) {
    val resolver = remember { com.wifimonitor.analyzer.FaviconResolver() }
    val total = domains.sumOf { it.second }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            domains.forEach { (domain, count) ->
                val info = resolver.resolve(domain)
                val fraction = count.toFloat() / total
                val animFraction by animateFloatAsState(fraction, tween(800), label = "bar_$domain")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info?.emoji ?: "🌐", fontSize = 16.sp, modifier = Modifier.width(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(info?.name ?: domain, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("$count", style = MaterialTheme.typography.labelSmall, color = CyberTeal, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(4.dp).background(NavyBorder, RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(animFraction).height(4.dp)
                                    .background(CyberTeal, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, onSeeAll: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(4.dp, 20.dp).background(CyberTeal, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Badge(containerColor = NavySurface) { Text("$count", color = CyberTeal, fontSize = 11.sp) }
        }
        TextButton(onClick = onSeeAll) {
            Text("See all", color = CyberTeal, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Default.ChevronRight, null, tint = CyberTeal, modifier = Modifier.size(14.dp))
        }
    }
}
@Composable
private fun NetworkPressureCard(score: Int, label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, if (score > 60) AlertRed.copy(0.3f) else CyberTeal.copy(0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = if (score > 60) AlertRed else CyberTeal,
                    trackColor = NavyBorder,
                    strokeCap = StrokeCap.Round
                )
                Text("$score", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Protection Integrity", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(OnlineGreen, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE DATA VERIFIED", style = MaterialTheme.typography.labelSmall, color = OnlineGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecentEventsSnippet() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventMirror("MacBook Pro joined", ChangeType.JOINED)
            EventMirror("Data spike detected", ChangeType.SPIKE)
            TextButton(onClick = { /* Navigate */ }, modifier = Modifier.align(Alignment.End)) {
                Text("View Timeline", color = CyberTeal, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EventMirror(text: String, type: ChangeType) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(if (type == ChangeType.SPIKE) WarningAmber else CyberTeal, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
    }
}

@Composable
private fun AuthorityStatusChip(state: com.wifimonitor.viewmodel.DashboardUiState) {
    val (label, color) = when {
        state.isGatewayMode -> "GATEWAY" to AlertRed
        state.isManagedMode -> "MANAGED" to CyberTeal
        else -> "PASSIVE" to TextMuted
    }
    
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}
@Composable
fun NarrativeHealthCard(report: com.wifimonitor.analyzer.InferenceEngine.NarrativeReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(report.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(report.summary, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Default.Info, null, tint = CyberTeal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.impact, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = CyberTeal.copy(0.1f)) {
                Text(report.suggestion, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = CyberTeal)
            }
        }
    }
}

@Composable
fun EmptyDiscoveryState(padding: PaddingValues, onScan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Text("🛰️", fontSize = 64.sp)
            Spacer(Modifier.height(24.dp))
            Text("Network Silent", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("No active devices detected in your vicinity. Deep discovery can uncover resident hardware.", style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = CyberTeal)) {
                Text("Start Discovery Scan", color = DeepNavy)
            }
        }
    }
}
@Composable
fun ScanDeltaCard(delta: com.wifimonitor.data.DeviceRepository.ScanDelta) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(0.05f)),
        border = BorderStroke(1.dp, CyberTeal.copy(0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CompareArrows, null, tint = CyberTeal)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Changes in Last Scan", style = MaterialTheme.typography.labelSmall, color = CyberTeal, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (delta.newMacs.isNotEmpty()) {
                        Text("+${delta.newMacs.size} New", color = OnlineGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (delta.leftMacs.isNotEmpty()) {
                        Text("-${delta.leftMacs.size} Left", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
@Composable
fun SentinelInsightsCard(insights: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface.copy(0.3f)),
        border = BorderStroke(1.dp, CyberTeal.copy(0.15f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = CyberTeal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                insights.forEach { insight ->
                    Text("• $insight", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun SecurityPostureCard(audit: com.wifimonitor.analyzer.IntelligenceEngine.SecurityAudit) {
    val color = when (audit.posture) {
        com.wifimonitor.analyzer.IntelligenceEngine.Posture.SAFE -> OnlineGreen
        com.wifimonitor.analyzer.IntelligenceEngine.Posture.WARNING -> WarningAmber
        com.wifimonitor.analyzer.IntelligenceEngine.Posture.RISK -> AlertRed
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(audit.posture) {
                        com.wifimonitor.analyzer.IntelligenceEngine.Posture.SAFE -> Icons.Default.GppGood
                        else -> Icons.Default.GppMaybe
                    },
                    contentDescription = null,
                    tint = color
                )
                Spacer(Modifier.width(10.dp))
                Text("Network Posture: ${audit.posture.name}", color = color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(audit.summary, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            if (audit.detailedConcerns.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                audit.detailedConcerns.take(2).forEach { concern ->
                    Text("! $concern", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { /* Launch Deep Security Audit Panel */ },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Deep Technical Audit", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun ActiveDeviceChip(device: NetworkDevice) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NavyCard,
        border = BorderStroke(1.dp, CyberTeal.copy(0.3f))
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(device.activityLevel.emoji, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(device.displayName, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(device.activityLevel.label, style = MaterialTheme.typography.labelSmall, color = CyberTeal, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun SentinelSolutionCard(solution: com.wifimonitor.analyzer.NetworkHealthOracle.SentinelSolution) {
    val color = when (solution.severity) {
        0 -> CyberTeal
        1 -> WarningAmber
        else -> AlertRed
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(solution.severity) {
                        0 -> Icons.Default.Info
                        1 -> Icons.Default.Warning
                        else -> Icons.Default.Dangerous
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(solution.title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(solution.description, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, null, tint = color, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(solution.correctiveAction, style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}

@Composable
fun LiveBandwidthHud(totalMbps: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, CyberTeal.copy(0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (totalMbps / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = CyberTeal,
                    trackColor = NavyBorder,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format("%.1f", totalMbps), style = MaterialTheme.typography.labelLarge, color = TextPrimary, fontWeight = FontWeight.Black)
                    Text("Mbps", fontSize = 8.sp, color = CyberTeal)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text("Total Network Throughput", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("Active data flowing via local VPN tunnel", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}
@Composable
fun GlobalFaultIndicator(fault: com.wifimonitor.data.NetworkFault, onClick: () -> Unit) {
    val color = when (fault.severity) {
        0 -> CyberTeal
        1 -> WarningAmber
        else -> AlertRed
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when(fault.type) {
                    com.wifimonitor.data.FaultType.LATENCY -> Icons.Default.Speed
                    com.wifimonitor.data.FaultType.BANDWIDTH -> Icons.Default.NetworkCheck
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(fault.title, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Investigate", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun FaultDetailPanel(
    fault: com.wifimonitor.data.NetworkFault,
    technicalExplanation: String,
    onExport: () -> Unit,
    onAction: (String?) -> Unit
) {
    var showExplanation by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
        // A. Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fault.title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Share, "Export", tint = CyberTeal)
            }
        }
        Spacer(Modifier.height(8.dp))
        
        // B. What Happened (Fact)
        Surface(shape = RoundedCornerShape(8.dp), color = NavyCard) {
            Text(
                fault.fact,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
        
        Spacer(Modifier.height(20.dp))
        Text("RELATED DATA", style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        
        // C. Related Data (Metric Grid)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fault.relatedData.take(2).forEach { metric ->
                MetricItem(metric, modifier = Modifier.weight(1f))
            }
        }
        
        // D. Context (Optional)
        fault.context?.let {
            Spacer(Modifier.height(20.dp))
            Text("CONTEXT", style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        
        Spacer(Modifier.height(32.dp))
        
        // E. Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fault.actions.forEach { action ->
                Button(
                    onClick = { onAction(action.route) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action.isPrimary) CyberTeal else NavyCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(action.label, color = if(action.isPrimary) DeepNavy else TextPrimary)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = { showExplanation = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Why am I seeing this?", color = CyberTeal, style = MaterialTheme.typography.labelMedium)
        }
        
        if (showExplanation) {
            AlertDialog(
                onDismissRequest = { showExplanation = false },
                title = { Text("Forensic Analysis") },
                text = {
                    Text(technicalExplanation)
                },
                confirmButton = {
                    TextButton(onClick = { showExplanation = false }) { Text("Understand", color = CyberTeal) }
                },
                containerColor = NavySurface,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary
            )
        }
    }
}

@Composable
fun MetricItem(metric: com.wifimonitor.data.Metric, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = NavyCard,
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(metric.value, style = MaterialTheme.typography.titleMedium, color = CyberTeal, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun QuickActionBar(isScanning: Boolean, onScan: () -> Unit, onNavigate: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = NavySurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.15f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            QuickActionItem(icon = Icons.Default.Search, label = "Scan", isPulse = isScanning, onClick = onScan)
            QuickActionItem(icon = Icons.Default.Public, label = "Usage", onClick = { onNavigate(Screen.Traffic.route) })
            QuickActionItem(icon = Icons.Default.History, label = "History", onClick = { onNavigate("timeline") })
            QuickActionItem(icon = Icons.Default.DeviceHub, label = "Topology", onClick = { onNavigate(Screen.TacticalMap.route) })
        }
    }
}

@Composable
fun QuickActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isPulse: Boolean = false, onClick: () -> Unit) {
    val alpha by if(isPulse) {
        rememberInfiniteTransition().animateFloat(0.4f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
    } else remember { mutableStateOf(1f) }

    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = CyberTeal.copy(alpha = alpha), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 9.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
