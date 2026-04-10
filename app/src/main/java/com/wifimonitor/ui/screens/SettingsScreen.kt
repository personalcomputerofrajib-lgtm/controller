package com.wifimonitor.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.ui.components.*
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Diagnostics", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Network Interface Status ──
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard), border = BorderStroke(1.dp, NavyBorder)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Network Interfaces", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        state.interfaces.forEach { iface ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                val emoji = when (iface.type) {
                                    "WIFI" -> "📶"; "ETHERNET" -> "🔌"; "HOTSPOT" -> "📡"; "CELLULAR" -> "📱"; else -> "🌐"
                                }
                                Text(emoji, fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${iface.name} (${iface.type})", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text("${iface.ip} · ${iface.ssid}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                                Box(modifier = Modifier.size(8.dp).background(OnlineGreen, shape = androidx.compose.foundation.shape.CircleShape))
                            }
                            HorizontalDivider(color = NavyBorder.copy(0.5f))
                        }
                        if (state.interfaces.isEmpty()) {
                            Text("No active interfaces detected", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ── Router Configuration ──
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard), border = BorderStroke(1.dp, if (state.routerConfigured) CyberTeal.copy(0.3f) else NavyBorder)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Router, null, tint = if (state.routerConfigured) CyberTeal else TextMuted)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Router Integration", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(if (state.routerConfigured) "Connected · ${state.routerHost}" else "Not configured",
                                    style = MaterialTheme.typography.labelSmall, color = if (state.routerConfigured) CyberTeal else TextMuted)
                            }
                            Switch(checked = state.managedMode, onCheckedChange = { viewModel.setManagedMode(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyberTeal, checkedTrackColor = CyberTeal.copy(0.3f)))
                        }

                        if (!state.routerConfigured) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = state.routerHostInput, onValueChange = viewModel::updateRouterHost,
                                label = { Text("Router IP", color = TextMuted) }, placeholder = { Text("192.168.1.1", color = TextMuted) },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberTeal, unfocusedBorderColor = NavyBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedContainerColor = NavySurface, unfocusedContainerColor = NavySurface))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = state.routerUserInput, onValueChange = viewModel::updateRouterUser,
                                    label = { Text("Username", color = TextMuted) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberTeal, unfocusedBorderColor = NavyBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedContainerColor = NavySurface, unfocusedContainerColor = NavySurface))
                                OutlinedTextField(value = state.routerPassInput, onValueChange = viewModel::updateRouterPass,
                                    label = { Text("Password", color = TextMuted) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberTeal, unfocusedBorderColor = NavyBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedContainerColor = NavySurface, unfocusedContainerColor = NavySurface))
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = viewModel::saveRouterConfig, colors = ButtonDefaults.buttonColors(containerColor = CyberTeal),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("Connect Router", color = DeepNavy, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Battery & Intelligence ──
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, if (state.efficiencyMode) OnlineGreen.copy(0.4f) else NavyBorder)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, null, tint = if (state.efficiencyMode) OnlineGreen else TextMuted)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery Efficiency", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(if (state.efficiencyMode) "Saves battery by using passive sensors" else "High-fidelity monitoring active", 
                                    style = MaterialTheme.typography.labelSmall, color = if (state.efficiencyMode) OnlineGreen else TextMuted)
                            }
                            Switch(checked = state.efficiencyMode, onCheckedChange = viewModel::toggleEfficiencyMode,
                                colors = SwitchDefaults.colors(checkedThumbColor = OnlineGreen, checkedTrackColor = OnlineGreen.copy(0.3f)))
                        }
                    }
                }
            }

            // ── Export Data ──
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard), border = BorderStroke(1.dp, NavyBorder)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Export Data", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::exportDevicesCsv, border = BorderStroke(1.dp, AccentBlue),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Devices CSV", color = AccentBlue, style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = viewModel::exportDevicesJson, border = BorderStroke(1.dp, AccentPurple),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Code, null, tint = AccentPurple, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Devices JSON", color = AccentPurple, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::exportTrafficCsv, border = BorderStroke(1.dp, CyberTeal),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null, tint = CyberTeal, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Traffic CSV", color = CyberTeal, style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = viewModel::exportHistoryCsv, border = BorderStroke(1.dp, WarningAmber),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Timeline, null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("History CSV", color = WarningAmber, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Button(onClick = { viewModel.exportForensicSnapshot(context) }, 
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTeal.copy(0.15f), contentColor = CyberTeal),
                            border = BorderStroke(1.dp, CyberTeal.copy(0.4f)),
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Analytics, null, tint = CyberTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export Full Forensic Snapshot (JSON)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        state.exportResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(result, style = MaterialTheme.typography.labelSmall, color = if (result.startsWith("✓")) OnlineGreen else AlertRed)
                        }
                    }
                }
            }

            // ── Diagnostic Mode ──
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, if (state.diagnosticMode) WarningAmber.copy(0.4f) else NavyBorder)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, null, tint = if (state.diagnosticMode) WarningAmber else TextMuted)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Diagnostic Mode", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Shows raw probe data, timings, state transitions", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            Switch(checked = state.diagnosticMode, onCheckedChange = viewModel::toggleDiagnostics,
                                colors = SwitchDefaults.colors(checkedThumbColor = WarningAmber, checkedTrackColor = WarningAmber.copy(0.3f)))
                        }
                    }
                }
            }

            // ── Diagnostic Log Entries ──
            if (state.diagnosticMode && state.diagEntries.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Diagnostic Log (${state.diagEntries.size})", style = MaterialTheme.typography.titleSmall, color = WarningAmber, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = viewModel::clearDiagnostics) {
                            Text("Clear", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                items(state.diagEntries.take(50)) { entry ->
                    val fmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
                    val catColor = when (entry.category) {
                        "SCAN" -> CyberTeal; "PROBE" -> AccentBlue; "STATE" -> AccentPurple
                        "DNS" -> OnlineGreen; "ERROR" -> AlertRed; "PERF" -> WarningAmber; else -> TextMuted
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = NavySurface)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(4.dp), color = catColor.copy(0.2f)) {
                                    Text(entry.category, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall, color = catColor, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(entry.time, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(entry.message, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            if (entry.details.isNotBlank()) {
                                Text("  └─ ${entry.details}", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
