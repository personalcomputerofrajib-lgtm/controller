package com.wifimonitor.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.data.AlertRule
import com.wifimonitor.data.RuleType
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.RuleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleManagerScreen(
    navController: NavController,
    viewModel: RuleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Alert Policies", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add Rule", tint = CyberTeal)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Surveillance Policies", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }

            if (state.rules.isEmpty()) {
                item {
                    EmptyRulesCard()
                }
            } else {
                items(state.rules) { rule ->
                    RuleTile(rule, viewModel::toggleRule, viewModel::deleteRule)
                }
            }
        }

        if (showAddDialog) {
            AddRuleDialog(
                onDismiss = { showAddDialog = false },
                devices = state.devicesForRules,
                onAdd = { mac, name, type, thr ->
                    viewModel.addRule(mac, name, type, thr)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun RuleTile(rule: AlertRule, onToggle: (AlertRule) -> Unit, onDelete: (AlertRule) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (rule.isEnabled) CyberTeal.copy(0.3f) else Color.Transparent)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (rule.type) {
                    RuleType.THRESHOLD_MB -> Icons.Default.DataUsage
                    RuleType.DEVICE_DISCONNECT -> Icons.Default.LinkOff
                    RuleType.DEVICE_RECONNECT -> Icons.Default.Link
                },
                null,
                tint = if (rule.isEnabled) CyberTeal else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.deviceName, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    when (rule.type) {
                        RuleType.THRESHOLD_MB -> "Alert when usage exceeds ${rule.thresholdValue} MB"
                        RuleType.DEVICE_DISCONNECT -> "Alert when device disconnects"
                        RuleType.DEVICE_RECONNECT -> "Alert when device re-appears"
                    },
                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                )
            }
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggle(rule) },
                colors = SwitchDefaults.colors(checkedThumbColor = CyberTeal, checkedTrackColor = CyberTeal.copy(0.3f))
            )
            IconButton(onClick = { onDelete(rule) }) {
                Icon(Icons.Default.Delete, null, tint = AlertRed.copy(0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EmptyRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Security, null, tint = TextMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("No active policies found", color = TextMuted)
            Text("Tap + to add a custom alert rule", style = MaterialTheme.typography.labelSmall, color = TextMuted.copy(0.7f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    devices: List<com.wifimonitor.data.NetworkDevice>,
    onAdd: (String, String, RuleType, Long) -> Unit
) {
    var selectedDevice by remember { mutableStateOf(devices.firstOrNull()) }
    var selectedType by remember { mutableStateOf(RuleType.DEVICE_DISCONNECT) }
    var threshold by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = { Text("New Alert Policy", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Select Target Device", style = MaterialTheme.typography.labelSmall, color = CyberTeal)
                // Note: Simplified selection for brevity. In a real app use a DropdownMenu.
                devices.take(5).forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selectedDevice?.mac == device.mac) CyberTeal.copy(0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .height(IntrinsicSize.Min)
                            .clickable { selectedDevice = device },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedDevice?.mac == device.mac, onClick = { selectedDevice = device })
                        Text(device.displayName, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text("2. Policy Action", style = MaterialTheme.typography.labelSmall, color = CyberTeal)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleChip("Disconnect", selectedType == RuleType.DEVICE_DISCONNECT) { selectedType = RuleType.DEVICE_DISCONNECT }
                    RuleChip("MB Limit", selectedType == RuleType.THRESHOLD_MB) { selectedType = RuleType.THRESHOLD_MB }
                }

                if (selectedType == RuleType.THRESHOLD_MB) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it.filter { c -> c.isDigit() } },
                        label = { Text("MB Threshold", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDevice?.let { onAdd(it.mac, it.displayName, selectedType, threshold.toLongOrNull() ?: 0) }
                },
                enabled = selectedDevice != null,
                colors = ButtonDefaults.buttonColors(containerColor = CyberTeal)
            ) {
                Text("Create Policy", color = DeepNavy)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun RuleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) CyberTeal else NavySurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) DeepNavy else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// Extension to allow clickable on basic modifier
private fun Modifier.clickable(onClick: () -> Unit) = this.then(
    Modifier.then(androidx.compose.foundation.clickable { onClick() })
)
