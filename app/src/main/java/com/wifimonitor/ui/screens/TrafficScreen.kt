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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wifimonitor.ui.components.*
import com.wifimonitor.ui.theme.*
import com.wifimonitor.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("DNS Activity", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.recentTraffic.size} queries captured",
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
        if (state.recentTraffic.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.NetworkCheck,
                    title = "No DNS activity yet",
                    subtitle = "mDNS monitoring will passively capture domain lookups on your network"
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Top domains summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Top Domains",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        state.topDomains.take(5).forEach { (domain, count) ->
                            TopDomainRow(domain = domain, count = count, total = state.recentTraffic.size)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Timeline
            item {
                Text(
                    "Activity Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(state.recentTraffic, key = { it.timestamp.toString() + it.domain }) { record ->
                TrafficItem(record = record, showTime = true)
            }
        }
    }
}

@Composable
private fun TopDomainRow(domain: String, count: Int, total: Int) {
    val fraction = count.toFloat() / total
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                domain,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = CyberTeal,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = CyberTeal,
            trackColor = NavyBorder
        )
    }
}
