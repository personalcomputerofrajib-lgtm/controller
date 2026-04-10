package com.wifimonitor.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberTeal,
    onPrimary = DeepNavy,
    primaryContainer = CyberTealDark,
    onPrimaryContainer = TextPrimary,
    secondary = AccentBlue,
    onSecondary = TextPrimary,
    secondaryContainer = NavySurface,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentPurple,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = NavyCard,
    onSurface = TextPrimary,
    surfaceVariant = NavySurface,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder,
    error = AlertRed,
    onError = Color.White
)

@Composable
fun WifiMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
