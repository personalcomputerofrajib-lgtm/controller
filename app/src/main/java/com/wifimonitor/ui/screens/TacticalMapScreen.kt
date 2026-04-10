package com.wifimonitor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.analyzer.SignalProcessor
import com.wifimonitor.data.NetworkDevice
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.DashboardViewModel
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticalMapScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel(),
    signalProcessor: SignalProcessor = hiltViewModel<com.wifimonitor.viewmodel.DiagnosticsViewModel>().let { SignalProcessor() } // Injected or accessible
) {
    // Note: In production, signalProcessor should be injected via Hilt properly.
    // For this implementation, we'll use a local helper for distance conversion.
    
    val state by viewModel.uiState.collectAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "tactical_map")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "sweep"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Tactical Mini-Map", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Active Environment Visualization", style = MaterialTheme.typography.labelSmall, color = CyberTeal)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Help */ }) {
                        Icon(Icons.Default.Info, null, tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard)
            )
        },
        containerColor = Color.Black // Dark slate for tactical feel
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // ── The Tactical Map Canvas ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(20.dp))
                    .background(DeepNavy)
            ) {
                TacticalMapCanvas(
                    devices = state.onlineDevices,
                    sweepAngle = sweepAngle,
                    onGetDistance = { rssi -> 
                        // Simplified FSPL for the UI
                        val baselineRssi = -35.0
                        val n = 3.2
                        Math.pow(10.0, (baselineRssi - rssi) / (10 * n))
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Overlay Coordinates
                Text(
                    "LAT: 40.7128° N | LON: 74.0060° W", 
                    modifier = Modifier.padding(12.dp).align(Alignment.BottomStart),
                    color = CyberTeal.copy(0.5f),
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                // You Marker
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Default.MyLocation, null, tint = CyberTeal, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Device Proximity List ──
            Text("Proximity Roster", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.onlineDevices.size) { index ->
                    val device = state.onlineDevices[index]
                    val dist = Math.pow(10.0, (-35.0 - device.signalStrength) / (10 * 3.2))
                    ProximityItem(device, dist)
                }
            }
        }
    }
}

@Composable
private fun TacticalMapCanvas(
    devices: List<NetworkDevice>,
    sweepAngle: Float,
    onGetDistance: (Int) -> Double,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxMapDimension = size.minDimension / 2 - 40.dp.toPx()
        
        // 1. Grid Lines (PUBG Style)
        val gridSize = 50.dp.toPx()
        for (x in 0..(size.width / gridSize).toInt()) {
            drawLine(NavyBorder.copy(0.2f), Offset(x * gridSize, 0f), Offset(x * gridSize, size.height), 0.5.dp.toPx())
        }
        for (y in 0..(size.height / gridSize).toInt()) {
            drawLine(NavyBorder.copy(0.2f), Offset(0f, y * gridSize), Offset(size.width, y * gridSize), 0.5.dp.toPx())
        }

        // 2. Distance Rings
        val ringCount = 4
        for (i in 1..ringCount) {
            val r = maxMapDimension * i / ringCount
            drawCircle(
                color = CyberTeal.copy(alpha = 0.1f),
                radius = r,
                center = center,
                style = Stroke(0.5.dp.toPx())
            )
            // Label
            // (Drawing text on canvas is complex in Compose, so we'll skip the ring labels for now 
            // and focus on the PUBG floating labels which are more interactive)
        }

        // 3. Radar Sweep
        drawArc(
            brush = Brush.sweepGradient(
                listOf(Color.Transparent, CyberTeal.copy(0.15f), Color.Transparent),
                center
            ),
            startAngle = sweepAngle - 30f,
            sweepAngle = 45f,
            useCenter = true,
            topLeft = Offset(center.x - maxMapDimension, center.y - maxMapDimension),
            size = androidx.compose.ui.geometry.Size(maxMapDimension * 2, maxMapDimension * 2)
        )

        // 4. Device Markers & Logic
        devices.forEach { device ->
            val dist = onGetDistance(device.signalStrength).toFloat()
            // Scaling logic: assume max distance we show is 20m
            val maxRange = 25f 
            val normalizedDist = (dist / maxRange).coerceIn(0.1f, 1f)
            val canvasDist = maxMapDimension * normalizedDist
            
            // Stable direction based on MAC (Random but consistent)
            val angle = (device.mac.hashCode().toFloat() % 360f)
            val dx = canvasDist * cos(Math.toRadians(angle.toDouble())).toFloat()
            val dy = canvasDist * sin(Math.toRadians(angle.toDouble())).toFloat()
            val markerPos = Offset(center.x + dx, center.y + dy)

            // Draw PUBG-style Marker
            drawCircle(color = if (device.signalStrength > -60) CyberTeal else WarningAmber, radius = 6.dp.toPx(), center = markerPos)
            drawCircle(color = Color.White.copy(0.3f), radius = 10.dp.toPx(), center = markerPos, style = Stroke(1.dp.toPx()))
            
            // Note: Floating labels (text) are best done as Composable overlays in a Box
            // But we'll do a simple visual "line" to indicate the sector for now.
        }
    }
    
    // 5. Floating Labels Overlay
    Box(modifier = modifier) {
        devices.forEach { device ->
            val dist = onGetDistance(device.signalStrength)
            val maxRange = 25f
            val normalizedDist = (dist.toFloat() / maxRange).coerceIn(0.1f, 1f)
            val angle = (device.mac.hashCode().toFloat() % 360f)
            
            // This is a bit tricky to sync with Canvas but works for visual demo
            // In a production app, we'd use a shared coordinate system.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cx = maxWidth / 2
                val cy = maxHeight / 2
                val r = (minOf(maxWidth, maxHeight) / 2 - 40.dp) * normalizedDist
                val dx = r * cos(Math.toRadians(angle.toDouble())).toFloat()
                val dy = r * sin(Math.toRadians(angle.toDouble())).toFloat()
                
                Box(
                    modifier = Modifier
                        .offset(x = cx + dx - 25.dp, y = cy + dy - 35.dp)
                        .background(Color.Black.copy(0.7f), RoundedCornerShape(4.dp))
                        .border(1.dp, CyberTeal.copy(0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${String.format("%.1fm", dist)}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProximityItem(device: NetworkDevice, distance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(CyberTeal.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(device.displayName.take(1), color = CyberTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.displayName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(device.ip, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format("%.1f m", distance), 
                    style = MaterialTheme.typography.titleSmall, 
                    color = CyberTeal, 
                    fontWeight = FontWeight.Bold
                )
                Text("Distance", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}
