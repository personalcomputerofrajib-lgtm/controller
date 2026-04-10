package com.wifimonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.wifimonitor.data.*
import com.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    navController: NavController,
    viewModel: com.wifimonitor.viewmodel.TimelineViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    replayViewModel: com.wifimonitor.viewmodel.HistoryReplayViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val events by viewModel.events.collectAsState(initial = emptyList())
    val replayState by replayViewModel.state.collectAsState()
    
    val scrubberActive = replayState.selectedTimestamp > 0 && 
                        Math.abs(replayState.selectedTimestamp - System.currentTimeMillis()) > 60000

    Scaffold(
        topBar = {
            TimelineTopBar(
                onBack = { navController.popBackStack() },
                scrubberActive = scrubberActive,
                replayState = replayState,
                onScrub = { replayViewModel.updateReplayTime(it) }
            )
        },
        containerColor = NavyBackground
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (scrubberActive) {
                ReplaySnapshotView(replayState.devicesAtTime)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(events) { event ->
                        TimelineEventCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(event: ChangeEvent) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val color = when (event.severity) {
        1 -> WarningAmber
        2 -> AlertRed
        else -> CyberTeal
    }

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Timeline connector
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Box(modifier = Modifier.width(2.dp).weight(1f).background(NavyBorder))
        }

        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt.format(Date(event.timestamp)), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(event.type.name, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Spacer(Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val typeIcon = when(event.type) {
                        ChangeType.JOINED -> "✨"
                        ChangeType.LEFT -> "🚫"
                        ChangeType.SPIKE -> "⚡"
                        ChangeType.ANOMALY -> "🚨"
                        ChangeType.WAKE -> "🔋"
                        ChangeType.SLEEP -> "💤"
                        else -> "📝"
                    }
                    Text(typeIcon, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(event.message, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(event.deviceMac, style = MaterialTheme.typography.labelSmall, color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
