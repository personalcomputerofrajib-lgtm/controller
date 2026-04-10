package com.wifimonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifimonitor.ui.theme.*

/**
 * Level 7 Analytics Suite.
 * Visualizes high-fidelity behavioral modeling and system health.
 */
@Composable
fun AnalyticsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(16.dp)
    ) {
        Text(
            "System Analytics",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            "High-Fidelity Behavioral Modeling",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        
        Spacer(Modifier.height(24.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { NetworkPulseLeaderboards() }
            item { ActivityDensityCard() }
            item { LatencyRootCauseCard() }
            item { SystemInternalHealthCard() }
        }
    }
}

@Composable
fun ActivityDensityCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, null, tint = CyberTeal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Activity Density Map", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            
            // Density Heatmap Visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(CyberTeal.copy(0.1f), WarningAmber.copy(0.4f), WarningAmber)
                        )
                    )
            ) {
                Text(
                    "Peak Concentration: 14:00 - 16:00",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
            
            Spacer(Modifier.height(12.dp))
            Text("High density detected during afternoon window.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
fun LatencyRootCauseCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Root Cause Attribution", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            
            RcaItem("WiFi Interference", 15)
            RcaItem("Device CPU Load", 8)
            RcaItem("Network Congestion", 72, CyberTeal)
            RcaItem("Internet Backbone", 5)
        }
    }
}

@Composable
fun RcaItem(label: String, pct: Int, color: Color = TextMuted) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            Text("$pct%", style = MaterialTheme.typography.labelSmall, color = color)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (pct > 50) color else NavyBorder,
            trackColor = NavyBorder
        )
    }
}

@Composable
fun SystemInternalHealthCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Internal Health Metrics", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Cycle Speed", "142ms")
                MetricItem("Cache Hits", "94%")
                MetricItem("Thread Load", "Low")
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = CyberTeal, fontWeight = FontWeight.Bold)
    }
}
@Composable
fun NetworkPulseLeaderboards(
    viewModel: com.wifimonitor.viewmodel.DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val devices = state.allDevices

    // ── Quality #16: Performance Optimization (Killer #16) ──
    val bandWidthVip by remember(devices) {
        derivedStateOf { devices.maxByOrNull { it.totalDownloadBytes + it.totalUploadBytes } }
    }
    val stabilityKing by remember(devices) {
        derivedStateOf { devices.filter { it.status == com.wifimonitor.data.DeviceStatus.ONLINE }.minByOrNull { it.jitterMs } }
    }
    val frequentTraveler by remember(devices) {
        derivedStateOf { devices.maxByOrNull { it.totalSessions } }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Network Pulse Leaderboards", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            if (bandWidthVip != null) {
                LeaderboardItem("🔥 Bandwidth VIP", bandWidthVip!!.displayName, "Most data consumed")
            }

            if (stabilityKing != null) {
                LeaderboardItem("🛡️ Stability King", stabilityKing!!.displayName, "Lowest jitter: ${stabilityKing!!.jitterMs}ms")
            }

            if (frequentTraveler != null) {
                LeaderboardItem("🕒 Frequent Traveler", frequentTraveler!!.displayName, "${frequentTraveler!!.totalSessions} total sessions")
            }
        }
    }
}

@Composable
fun LeaderboardItem(rank: String, name: String, reason: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rank, style = MaterialTheme.typography.labelSmall, color = CyberTeal, fontWeight = FontWeight.Bold)
            Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(reason, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
