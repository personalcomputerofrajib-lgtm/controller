package com.wifimonitor.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.wifimonitor.ui.theme.*
import com.wifimonitor.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Control", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard)
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Managed Mode Toggle Card
            ManagedModeCard()
            
            // 2. Gaming Boost Card
            GamingBoostCard()

            // 3. ISP Health Status
            IspHealthStatusCard()

            // 4. Router Integration Status
            RouterStatusCard()

            // 3. Global Controls
            SectionHeader(title = "Global Rules")
            ControlActionTile(
                title = "Block Unknown Devices",
                subtitle = "Automatically block any new device connecting to WiFi",
                icon = Icons.Default.Security,
                color = AlertRed
            )
            ControlActionTile(
                title = "Internet Schedule",
                subtitle = "Set hours when internet is available",
                icon = Icons.Default.Schedule,
                color = WarningAmber
            )
            ControlActionTile(
                title = "Parental Filters",
                subtitle = "Block adult content at DNS level",
                icon = Icons.Default.FamilyRestroom,
                color = CyberTeal
            )

            // 4. Tactical Intelligence
            SectionHeader(title = "Tactical Intelligence")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = { navController.navigate("rule_manager") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Security, null, tint = CyberTeal, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Alert Policies", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Custom rules", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                Surface(
                    onClick = { navController.navigate("tactical_map") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Radar, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Tactical Map", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Unit presence", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagedModeCard() {
    var isEnabled by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, if (isEnabled) CyberTeal.copy(0.5f) else NavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (isEnabled) CyberTeal.copy(0.15f) else NavySurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    null,
                    tint = if (isEnabled) CyberTeal else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Managed Network Mode", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    if (isEnabled) "Real-time control active" else "Passive monitoring only",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) CyberTeal else TextMuted
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CyberTeal
                )
            )
        }
    }
}

@Composable
private fun GamingBoostCard() {
    var isEnabled by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(0.1f)),
        border = BorderStroke(1.dp, if (isEnabled) AccentPurple else NavyBorder)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, null, tint = AccentPurple, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Gaming Boost (Low Latency)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(if (isEnabled) "Optimizing for zero jitter" else "Standard network behavior", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentPurple))
        }
    }
}

@Composable
private fun IspHealthStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudQueue, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ISP Backbone Health", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text("OPTIMAL", style = MaterialTheme.typography.labelSmall, color = OnlineGreen)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(OnlineGreen, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("ISP Ping: 12ms | Jitter: 2ms", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun RouterStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Router, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Router Integration", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Text("OpenWrt", style = MaterialTheme.typography.labelSmall, color = CyberTeal)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(OnlineGreen, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("API Connected (192.168.1.1)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun ControlActionTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyBorder)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = TextMuted,
        modifier = Modifier.padding(top = 8.dp)
    )
}
