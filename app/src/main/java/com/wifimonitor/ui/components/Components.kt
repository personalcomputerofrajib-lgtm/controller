package com.wifimonitor.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.wifimonitor.analyzer.FaviconResolver
import com.wifimonitor.data.*
import com.wifimonitor.ui.Screen
import com.wifimonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppBottomBar(navController: NavController) {
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    NavigationBar(containerColor = NavyCard, tonalElevation = 0.dp) {
        listOf(
            Triple(Icons.Default.Home, "Dashboard", Screen.Dashboard.route),
            Triple(Icons.Default.Devices, "Devices", Screen.DeviceList.route),
            Triple(Icons.Default.NetworkCheck, "Traffic", Screen.Traffic.route),
            Triple(Icons.Default.Notifications, "Alerts", Screen.Alerts.route)
        ).forEach { (icon, label, route) ->
            NavigationBarItem(
                icon = { Icon(icon, label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) navController.navigate(route) {
                        launchSingleTop = true; restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyberTeal,
                    selectedTextColor = CyberTeal,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = CyberTeal.copy(alpha = 0.12f)
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Premium Device Card with Activity Meter
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeviceCard(
    device: NetworkDevice, 
    history: List<Float> = emptyList(),
    onClick: () -> Unit, 
    onFaultClick: (() -> Unit)? = null
) {
    val isOnline = device.status == DeviceStatus.ONLINE
    val actColor = activityColor(device.activityLevel)

    val infiniteTransition = rememberInfiniteTransition(label = "card_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "dot_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(
            1.dp,
            if (isOnline && device.activityLevel != ActivityLevel.IDLE)
                actColor.copy(alpha = 0.4f) else NavyBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Device icon with glow
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            brush = if (isOnline) Brush.radialGradient(
                                listOf(
                                    deviceTypeColor(device.deviceType).copy(alpha = if (isOnline) glowAlpha * 0.3f else 0.1f),
                                    Color.Transparent
                                )
                            ) else Brush.radialGradient(listOf(Color.Transparent, Color.Transparent)),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .background(
                            deviceTypeColor(device.deviceType).copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = deviceTypeIcon(device.deviceType),
                        contentDescription = null,
                        tint = deviceTypeColor(device.deviceType),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            device.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (device.isBlocked) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = AlertRed.copy(alpha = 0.15f)) {
                                Text("BLOCKED", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = AlertRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        buildString {
                            append(device.manufacturer.ifBlank { "Unknown" })
                            if (device.ip.isNotBlank()) append(" · ${device.ip}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status dot + label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(if (isOnline) pulseScale else 1f)
                                .background(
                                    color = if (isOnline) OnlineGreen else OfflineGray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnline) OnlineGreen else OfflineGray
                        )
                    }
                    if (isOnline) {
                        val mbps = device.downloadRateMbps
                        val mbpsColor = when {
                            mbps > 10f -> AlertRed
                            mbps > 2f -> WarningAmber
                            else -> CyberTeal
                        }
                        Text(
                            String.format("%.1f Mbps", mbps),
                            style = MaterialTheme.typography.labelSmall,
                            color = mbpsColor,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }

            // Activity bar row (only for online devices)
            if (isOnline) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        ActivityMeterBar(level = device.activityLevel, label = device.behaviorLabel.ifBlank { device.activityLevel.label })
                    }
                    if (history.isNotEmpty()) {
                        ActivitySparkline(
                            history = history,
                            modifier = Modifier.width(80.dp).height(24.dp)
                        )
                    }
                }
            }

            // Smart tags
            val tags = device.smartTagsList()
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tags) { tag -> SmartTag(tag) }
                }
            }

            // Favicon domain strip
            val domains = device.recentDomainsList()
            if (domains.isNotEmpty() && isOnline) {
                Spacer(Modifier.height(8.dp))
                DomainIconStrip(domains = domains)
            }

            // --- Level 8: Device Fault HUD ---
            if (isOnline && device.downloadRateMbps > 5.0f) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AlertRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = AlertRed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Heavy Usage Detected",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlertRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AlertRed.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onFaultClick?.invoke() }
                        ) {
                            Text(
                                "Details",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertRed,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity Meter Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActivityMeterBar(level: ActivityLevel, label: String) {
    val color = activityColor(level)
    val bars = level.ordinal + 1  // 1-5 bars
    val animatedBars by animateIntAsState(targetValue = bars, animationSpec = tween(600), label = "bars")

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 5 bars
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..5) {
                val active = i <= animatedBars
                val barColor = if (active) color else NavyBorder
                val height = (8 + i * 3).dp
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(height)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Smart Tag Chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SmartTag(tag: String) {
    val (icon, color) = smartTagVisuals(tag)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 10.sp)
            Spacer(Modifier.width(4.dp))
            Text(tag, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

private fun smartTagVisuals(tag: String): Pair<String, Color> = when {
    tag.contains("Primary") -> "⭐" to AccentBlue
    tag.contains("Heavy") -> "🔥" to AlertRed
    tag.contains("Always") -> "🟢" to OnlineGreen
    tag.contains("Streaming") -> "▶️" to AccentPurple
    tag.contains("New") -> "✨" to WarningAmber
    tag.contains("Background") -> "⚙️" to TextSecondary
    tag.contains("Frequently") -> "📊" to CyberTeal
    else -> "🏷️" to TextSecondary
}

// ─────────────────────────────────────────────────────────────────────────────
// Domain Icon Strip (Favicon row)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DomainIconStrip(domains: List<String>) {
    val resolver = remember { com.wifimonitor.analyzer.FaviconResolver() }
    val resolved = remember(domains) { resolver.resolveAll(domains) }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(resolved.filter { it.second != null }) { (domain, info) ->
            info?.let {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavySurface,
                    border = BorderStroke(0.5.dp, NavyBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it.emoji, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            it.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Network Health Gauge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HealthScoreRing(score: Int, label: String, modifier: Modifier = Modifier) {
    val color = when {
        score >= 90 -> OnlineGreen
        score >= 75 -> CyberTeal
        score >= 60 -> WarningAmber
        score >= 40 -> AccentOrange
        else -> AlertRed
    }

    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(1000, easing = EaseOutCubic),
        label = "score_anim"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val radius = size.minDimension / 2 - strokeWidth
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)

            // Background arc
            drawArc(
                color = NavyBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Score arc
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * (animatedScore / 100f),
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${score}",
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Traffic Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrafficItem(record: TrafficRecord, showTime: Boolean = false) {
    val resolver = remember { com.wifimonitor.analyzer.FaviconResolver() }
    val info = remember(record.domain) { resolver.resolve(record.domain) }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyCard, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Domain icon
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AccentPurple.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(info?.emoji ?: "🌐", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info?.name ?: record.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.isBlocked) AlertRed else TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (record.isBlocked) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = AlertRed.copy(alpha = 0.15f)) {
                        Text("BLOCKED", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                    }
                }
            }
            Text(
                if (info != null) record.domain else "from ${record.deviceMac.take(14)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showTime) {
            Text(
                fmt.format(Date(record.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary Stat Card (header row)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SummaryStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    subtitle: String = ""
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Device Group Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GroupHeader(group: DeviceGroup, count: Int) {
    val (icon, label, color) = groupVisuals(group)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Badge(containerColor = color.copy(alpha = 0.15f)) {
            Text("$count", color = color, fontSize = 11.sp)
        }
    }
}

private fun groupVisuals(group: DeviceGroup): Triple<String, String, Color> = when (group) {
    DeviceGroup.MY_DEVICES -> Triple("📱", "My Devices", AccentBlue)
    DeviceGroup.FAMILY -> Triple("👨‍👩‍👧", "Family", AccentPurple)
    DeviceGroup.UNKNOWN -> Triple("❓", "Unknown", WarningAmber)
    DeviceGroup.IOT -> Triple("⚙️", "IoT Devices", CyberTeal)
}

// ─────────────────────────────────────────────────────────────────────────────
// Status Chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusChip(status: DeviceStatus) {
    val (color, label) = when (status) {
        DeviceStatus.ONLINE -> OnlineGreen to "Online"
        DeviceStatus.OFFLINE -> OfflineGray to "Offline"
        DeviceStatus.UNKNOWN -> TextMuted to "Unknown"
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State & Error Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(80.dp).background(NavyCard, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = CyberTeal), shape = RoundedCornerShape(10.dp)) {
                Text(action, color = DeepNavy, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = AlertRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = AlertRed, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, tint = AlertRed)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

fun deviceTypeIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.PHONE -> Icons.Default.PhoneAndroid
    DeviceType.LAPTOP -> Icons.Default.Laptop
    DeviceType.TV -> Icons.Default.Tv
    DeviceType.ROUTER -> Icons.Default.Router
    DeviceType.IOT -> Icons.Default.Memory
    DeviceType.TABLET -> Icons.Default.TabletAndroid
    DeviceType.UNKNOWN -> Icons.Default.DevicesOther
}

fun deviceTypeColor(type: DeviceType): Color = when (type) {
    DeviceType.PHONE -> AccentBlue
    DeviceType.LAPTOP -> AccentPurple
    DeviceType.TV -> AccentOrange
    DeviceType.ROUTER -> CyberTeal
    DeviceType.IOT -> WarningAmber
    DeviceType.TABLET -> AccentBlue
    DeviceType.UNKNOWN -> TextSecondary
}

fun activityColor(level: ActivityLevel): Color = when (level) {
    ActivityLevel.IDLE -> TextMuted
    ActivityLevel.LOW -> OnlineGreen
    ActivityLevel.BROWSING -> CyberTeal
    ActivityLevel.ACTIVE -> WarningAmber
    ActivityLevel.HEAVY -> AlertRed
}
