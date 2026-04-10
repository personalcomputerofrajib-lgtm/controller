package com.wifimonitor.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.data.AlertRecord
import com.wifimonitor.data.AlertType
import com.wifimonitor.ui.components.AppBottomBar
import com.wifimonitor.ui.components.EmptyState
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.markAlertsRead() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alerts", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.alerts.size} total alerts",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard)
            )
        },
        bottomBar = { AppBottomBar(navController) },
        containerColor = DeepNavy
    ) { padding ->
        if (state.alerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Notifications,
                    title = "No alerts",
                    subtitle = "You'll be notified when new devices join your network"
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.alerts) { alert ->
                AlertCard(alert = alert)
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertRecord) {
    val fmt = remember { SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()) }
    val (icon, color, label) = alertVisuals(alert.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, if (!alert.isRead) color.copy(alpha = 0.4f) else NavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        fmt.format(Date(alert.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (!alert.isRead) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "NEW",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun alertVisuals(type: AlertType): Triple<androidx.compose.ui.graphics.vector.ImageVector, Color, String> =
    when (type) {
        AlertType.NEW_DEVICE -> Triple(Icons.Default.DeviceUnknown, WarningAmber, "New Device Detected")
        AlertType.DEVICE_BLOCKED -> Triple(Icons.Default.Block, AlertRed, "Device Blocked")
        AlertType.ACTIVITY_SPIKE -> Triple(Icons.Default.TrendingUp, AccentPurple, "Activity Spike")
        AlertType.ANOMALY -> Triple(Icons.Default.Warning, AlertRed, "Network Anomaly")
        AlertType.STABILITY_DROP -> Triple(Icons.Default.WifiTetheringOff, WarningAmber, "Stability Drop")
        AlertType.PATTERN_CHANGE -> Triple(Icons.Default.Update, AccentBlue, "Pattern Change")
        AlertType.HIDDEN_CAMERA_FOUND -> Triple(Icons.Default.CameraAlt, AlertRed, "Hidden Camera Found")
        AlertType.SUSPICIOUS_BEHAVIOR -> Triple(Icons.Default.Security, WarningAmber, "Suspicious Behavior")
        AlertType.PORT_CHANGE -> Triple(Icons.Default.SettingsEthernet, AccentBlue, "Port Configuration Change")
    }
