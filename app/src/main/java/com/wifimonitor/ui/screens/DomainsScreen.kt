package com.wifimonitor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.wifimonitor.analyzer.FaviconResolver
import com.wifimonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainsScreen(navController: NavController) {
    val resolver = remember { FaviconResolver() }
    
    // Mock data for now — will be connected to ViewModel
    val domains = listOf(
        "youtube.com", "google.com", "instagram.com", "netflix.com",
        "github.com", "reddit.com", "amazon.com", "microsoft.com",
        "openai.com", "twitter.com", "discord.com", "spotify.com"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Domains", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyCard),
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = TextSecondary) }
                }
            )
        },
        containerColor = DeepNavy
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            
            items(domains) { domain ->
                DomainRow(domain, resolver)
            }
        }
    }
}

@Composable
private fun DomainRow(domain: String, resolver: FaviconResolver) {
    val info = resolver.resolve(domain)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favicon Loader
            if (info?.iconUrl != null) {
                AsyncImage(
                    model = info.iconUrl,
                    contentDescription = domain,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavySurface),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(NavySurface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(info?.emoji ?: "🌐", fontSize = 18.sp)
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info?.name ?: domain,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("12 queries", style = MaterialTheme.typography.labelSmall, color = CyberTeal)
                Text("2m ago", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}
