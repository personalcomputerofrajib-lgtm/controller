package com.wifimonitor.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Radar
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
import com.wifimonitor.data.NetworkDevice
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.DashboardViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "heatmap")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sweep"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signal Heatmap", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard)
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Environment RF Analysis",
                style = MaterialTheme.typography.labelSmall,
                color = CyberTeal,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ── The Heatmap Canvas ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, NavyBorder), RoundedCornerShape(24.dp))
                    .background(Color.Black)
            ) {
                RadarHeatmapCanvas(
                    devices = state.onlineDevices,
                    sweepAngle = sweepAngle,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Legend & Status ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Radar, null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Active Wave Interference", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Visualizing signal density and device spatial mapping based on RSSI telemetry.",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendItem("High Density", AlertRed)
                        LegendItem("Stable", CyberTeal)
                        LegendItem("Fringe", TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarHeatmapCanvas(
    devices: List<NetworkDevice>,
    sweepAngle: Float,
    modifier: Modifier = Modifier
) {
    val centerAlpha by rememberInfiniteTransition("pulse").animateFloat(
        0.2f, 0.4f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), "a"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2 - 20.dp.toPx()

        // 1. Background Grid
        for (i in 1..4) {
            drawCircle(
                color = NavyBorder.copy(alpha = 0.3f),
                radius = maxRadius * i / 4,
                center = center,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            )
        }

        // 2. Heat Blobs for Devices
        devices.forEachIndexed { index, device ->
            // Distribute devices pseudo-randomly but deterministically based on MAC
            val angle = (device.mac.hashCode().toFloat() % 360f)
            val distanceFactor = (100 - device.signalStrength.coerceIn(0, 100)) / 100f
            val distance = maxRadius * (0.2f + 0.6f * distanceFactor)
            
            val deviceX = center.x + distance * cos(Math.toRadians(angle.toDouble())).toFloat()
            val deviceY = center.y + distance * sin(Math.toRadians(angle.toDouble())).toFloat()
            val devicePos = Offset(deviceX, deviceY)

            // Dynamic Heat Bloom
            val color = when {
                device.signalStrength > 80 -> CyberTeal
                device.signalStrength > 40 -> WarningAmber
                else -> AlertRed
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                    center = devicePos,
                    radius = 80.dp.toPx()
                ),
                radius = 80.dp.toPx(),
                center = devicePos
            )
            
            drawCircle(color = color, radius = 4.dp.toPx(), center = devicePos)
            drawCircle(color = color.copy(0.3f), radius = 8.dp.toPx(), center = devicePos, style = Stroke(1.dp.toPx()))
        }

        // 3. Radar Sweep Beam
        drawArc(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                0.1f to CyberTeal.copy(alpha = 0.4f),
                0.2f to Color.Transparent,
                center = center
            ),
            startAngle = sweepAngle - 90f,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
            size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
        )
        
        // Sweep line
        val sweepLineX = center.x + maxRadius * cos(Math.toRadians(sweepAngle.toDouble() - 30)).toFloat()
        val sweepLineY = center.y + maxRadius * sin(Math.toRadians(sweepAngle.toDouble() - 30)).toFloat()
        drawLine(
            color = CyberTeal.copy(alpha = 0.6f),
            start = center,
            end = Offset(sweepLineX, sweepLineY),
            strokeWidth = 1.5.dp.toPx()
        )

        // 4. Center Primary Hub
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(CyberTeal.copy(alpha = centerAlpha), Color.Transparent),
                center = center,
                radius = 40.dp.toPx()
            ),
            radius = 40.dp.toPx(),
            center = center
        )
        drawCircle(CyberTeal, 6.dp.toPx(), center)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
