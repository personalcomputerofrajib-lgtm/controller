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
import androidx.navigation.NavController
import com.wifimonitor.rules.RuleEngine
import com.wifimonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(navController: NavController) {
    // Mock rules for UI demonstration
    val rules = remember {
        mutableStateListOf(
            RuleEngine.Rule("1", RuleEngine.RuleType.DOMAIN, null, "tiktok.com", RuleEngine.Action.BLOCK),
            RuleEngine.Rule("2", RuleEngine.RuleType.SCHEDULE, "00:11:22:33:44:55", "22:00-07:00", RuleEngine.Action.BLOCK),
            RuleEngine.Rule("3", RuleEngine.RuleType.GLOBAL_BLOCK, "AA:BB:CC:DD:EE:FF", "Block All", RuleEngine.Action.THROTTLE)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automation Rules", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard),
                actions = {
                    IconButton(onClick = { /* Add Rule */ }) {
                        Icon(Icons.Default.Add, null, tint = CyberTeal)
                    }
                }
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
                Text("Active Policies", style = MaterialTheme.typography.labelLarge, color = TextMuted)
            }
            
            items(rules) { rule ->
                RuleCard(rule)
            }
            
            item {
                Spacer(Modifier.height(8.dp))
                SecuritySuggestionCard()
            }
        }
    }
}

@Composable
private fun RuleCard(rule: RuleEngine.Rule) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when (rule.type) {
                RuleEngine.RuleType.DOMAIN -> Icons.Default.Public to CyberTeal
                RuleEngine.RuleType.SCHEDULE -> Icons.Default.Schedule to WarningAmber
                RuleEngine.RuleType.GLOBAL_BLOCK -> Icons.Default.Block to AlertRed
                RuleEngine.RuleType.CATEGORY -> Icons.Default.Category to AccentBlue
            }
            
            Box(
                modifier = Modifier.size(44.dp).background(color.copy(0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when(rule.type) {
                        RuleEngine.RuleType.DOMAIN -> "Block Domain"
                        RuleEngine.RuleType.SCHEDULE -> "Time Schedule"
                        RuleEngine.RuleType.GLOBAL_BLOCK -> "Global Block"
                        RuleEngine.RuleType.CATEGORY -> "App Filter"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    if (rule.deviceMac == null) "Global Rule: ${rule.target}" 
                    else "Device Rule: ${rule.target}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { /* Toggle */ },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = color)
            )
        }
    }
}

@Composable
private fun SecuritySuggestionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberTeal.copy(0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberTeal.copy(0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💡", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AI Suggestion", style = MaterialTheme.typography.labelMedium, color = CyberTeal)
                Text("Block unknown devices at night for better security.", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            }
        }
    }
}
