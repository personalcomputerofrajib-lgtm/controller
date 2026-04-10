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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.data.*
import com.wifimonitor.ui.Screen
import com.wifimonitor.ui.components.*
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showOnlineOnly by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<DeviceGroup?>(null) }
    var sortBy by remember { mutableStateOf(SortOrder.LAST_SEEN) }
    var groupByCategory by remember { mutableStateOf(false) }

    val filtered = remember(state.allDevices, searchQuery, showOnlineOnly, selectedGroup, sortBy) {
        var list = state.allDevices
        if (showOnlineOnly) list = list.filter { it.status == DeviceStatus.ONLINE }
        selectedGroup?.let { g -> list = list.filter { it.deviceGroup == g } }
        if (searchQuery.isNotBlank()) list = list.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.ip.contains(searchQuery) ||
            it.mac.contains(searchQuery, ignoreCase = true) ||
            it.manufacturer.contains(searchQuery, ignoreCase = true)
        }
        when (sortBy) {
            SortOrder.LAST_SEEN -> list.sortedByDescending { it.lastSeen }
            SortOrder.IP -> list.sortedBy { it.ip }
            SortOrder.NAME -> list.sortedBy { it.displayName }
            SortOrder.STATUS -> list.sortedByDescending { it.status == DeviceStatus.ONLINE }
            SortOrder.ACTIVITY -> list.sortedByDescending { it.activityLevel.ordinal }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("All Devices", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${state.allDevices.size} total · ${state.onlineDevices.size} online",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Search name, IP, MAC…", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null, tint = TextSecondary) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberTeal, unfocusedBorderColor = NavyBorder, focusedContainerColor = NavyCard, unfocusedContainerColor = NavyCard, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
            )

            // Group filter chips
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = selectedGroup == null, onClick = { selectedGroup = null },
                        label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyberTeal.copy(0.2f), selectedLabelColor = CyberTeal))
                }
                items(DeviceGroup.entries) { group ->
                    val (emoji, label, color) = when (group) {
                        DeviceGroup.MY_DEVICES -> Triple("📱", "Mine", AccentBlue)
                        DeviceGroup.FAMILY -> Triple("👨‍👩‍👧", "Family", AccentPurple)
                        DeviceGroup.UNKNOWN -> Triple("❓", "Unknown", WarningAmber)
                        DeviceGroup.IOT -> Triple("⚙️", "IoT", CyberTeal)
                    }
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = if (selectedGroup == group) null else group },
                        label = { Text("$emoji $label", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(0.15f), selectedLabelColor = color)
                    )
                }
                item {
                    FilterChip(selected = showOnlineOnly, onClick = { showOnlineOnly = !showOnlineOnly },
                        label = { Text("🟢 Online", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OnlineGreen.copy(0.15f), selectedLabelColor = OnlineGreen))
                }
            }

            Spacer(Modifier.height(4.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(icon = Icons.Default.DevicesOther, title = "No devices found",
                            subtitle = if (searchQuery.isNotBlank()) "Try a different search term" else "Run a scan from the dashboard")
                    }
                } else {
                    items(filtered, key = { it.mac }) { device ->
                        AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                            DeviceCard(device = device, onClick = {
                                navController.navigate(Screen.DeviceDetail.createRoute(device.mac))
                            })
                        }
                    }
                }
            }
        }
    }
}

enum class SortOrder { LAST_SEEN, IP, NAME, STATUS, ACTIVITY }
