package com.wifimonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifimonitor.ui.theme.*

import androidx.hilt.navigation.compose.hiltViewModel
import com.wifimonitor.viewmodel.DiagnosticsViewModel

/**
 * Level 7 Engineering Component: Diagnostics Toolkit.
 * Fulfills Principle #9: Serious Diagnostics value.
 */
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val auditReport by viewModel.auditReport.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(16.dp)
    ) {
        Text("Engineering Toolkit", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Text("Real-world verification & diagnostics", style = MaterialTheme.typography.bodySmall, color = TextMuted)

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Interactive Probes ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProbeCard("Ping Sweep", Icons.Default.SwapHoriz, "ICMP / All", Modifier.weight(1f)) {
                        viewModel.runPingSweep()
                    }
                    ProbeCard("Gateway", Icons.Default.Router, "Router/DNS", Modifier.weight(1f)) {
                        viewModel.runGatewayCheck()
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProbeCard("Anti-Spy Scan", Icons.Default.CameraAlt, "Hidden Cameras", Modifier.weight(1f)) {
                        viewModel.runAntiSpyScan()
                    }
                    ProbeCard("Service Prober", Icons.Default.Dns, "Port Discovery", Modifier.weight(1f)) {
                        viewModel.runServiceDiscovery()
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProbeCard("Traceroute", Icons.Default.Timeline, "Hop Mapping", Modifier.weight(1f)) {
                        viewModel.runTraceroute()
                    }
                    ProbeCard("Speed Test", Icons.Default.Speed, "Network Bandwidth", Modifier.weight(1f)) {
                        viewModel.runSpeedTest()
                    }
                }
            }

            // ── 2. Forensic Audit Section ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberTeal.copy(0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(androidx.compose.material.icons.Icons.Default.VerifiedUser, null, tint = CyberTeal)
                            Spacer(Modifier.width(10.dp))
                            Text("Security Forensic Audit", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Generates a professional narrative summary of current network security, vulnerabilities, and presence status.", 
                            style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Spacer(Modifier.height(16.dp))
                        
                        if (auditReport == null) {
                            Button(
                                onClick = viewModel::generateNarrativeAudit,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberTeal)
                            ) {
                                Text("Generate Full Audit", color = DeepNavy, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(0.3f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, CyberTeal.copy(0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(auditReport!!, 
                                        style = MaterialTheme.typography.bodySmall, 
                                        color = TextPrimary, 
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(onClick = viewModel::clearAudit, modifier = Modifier.align(Alignment.End)) {
                                        Text("Dismiss Report", color = CyberTeal, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Military-Grade Console ──
            item {
                Text("Live Diagnostic Output", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp) // Fixed height inside LazyColumn
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    LazyColumn {
                        items(consoleLogs.size) { index ->
                            Text(
                                "> ${consoleLogs[index]}",
                                color = CyberTeal,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbeCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
