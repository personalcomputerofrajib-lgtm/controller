package com.wifimonitor.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.wifimonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    navController: NavController,
    viewModel: com.wifimonitor.viewmodel.ForensicsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Activity", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard)
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Forensics Hub
            item { ForensicsHubCard() }

            // 1. Current Speed Stats
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpeedCard(Modifier.weight(1f), "Total Down", formatBytes(state.totalDownload), Icons.Default.Download, CyberTeal)
                    SpeedCard(Modifier.weight(1f), "Total Up", formatBytes(state.totalUpload), Icons.Default.Upload, WarningAmber)
                }
            }

            // 2. Traffic Graph
            item { TrafficGraphCard(state.trafficHistory.map { it.value }) }

            // 2.5 DPI Classification
            item { DPIClassificationCard(state.classification) }

            // 3. Top Consuming Devices
            item { Text("Top Bandwidth Usage", style = MaterialTheme.typography.labelLarge, color = TextMuted) }
            
            items(state.topUsers) { user ->
                UsageTile(user.name, user.bytes, user.percent)
            }
        }
    }
}

@Composable
private fun SpeedCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun TrafficGraphCard(points: List<Float>) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (points.isEmpty()) return@Canvas
                val path = Path()
                val width = size.width
                val height = size.height
                
                path.moveTo(0f, height * (1 - points[0]))
                points.forEachIndexed { i, p ->
                    if (i > 0) {
                        path.lineTo(width * i / (points.size - 1), height * (1 - p))
                    }
                }
                
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(listOf(CyberTeal, Color.Transparent)),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Text("Network Load (History)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun UsageTile(name: String, bytes: Long, percent: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).background(DeepNavy, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (name.contains("Phone", true)) "📱" else "💻", fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = CyberTeal,
                    trackColor = NavyBorder
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(formatBytes(bytes), style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

@Composable
private fun ForensicsHubCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberTeal.copy(0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Forensics Hub", style = MaterialTheme.typography.titleSmall, color = CyberTeal)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { /* Export CSV */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FileDownload, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Audit Log", color = TextPrimary, fontSize = 12.sp)
                }
                Button(
                    onClick = { /* Export PDF */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Description, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Full Report", color = TextPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DPIClassificationCard(classification: Map<String, Float>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Traffic Classification (DPI)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            
            if (classification.isEmpty()) {
                Text("Analyzing traffic patterns...", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            } else {
                classification.entries.sortedByDescending { it.value }.forEach { (label, percent) ->
                    val color = when(label) {
                        "Streaming" -> CyberTeal; "Gaming" -> AccentPurple
                        "Social" -> WarningAmber; "System" -> AccentBlue
                        else -> TextMuted
                    }
                    DPIBar(label, percent, color)
                }
            }
        }
    }
}

@Composable
private fun DPIBar(label: String, percent: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("${(percent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = NavyBorder
        )
    }
}
