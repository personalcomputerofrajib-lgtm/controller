package com.wifimonitor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wifimonitor.ui.theme.*

/**
 * Sparkline — a compact inline graph showing data trends.
 * Used for: activity history, latency history, per-device mini-graphs.
 */
@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = CyberTeal,
    fillColor: Color = CyberTeal.copy(alpha = 0.08f),
    strokeWidth: Float = 2f,
    showDot: Boolean = true,
    label: String? = null,
    valueLabel: String? = null
) {
    if (data.isEmpty()) return

    Column(modifier = modifier) {
        if (label != null || valueLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                label?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                valueLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = lineColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Quality #16: Zero-Allocation Drawing (Audit 5) ──
        val points = remember(data) {
            val count = data.size
            if (count < 2) emptyList()
            else {
                val min = data.min()
                val max = data.max().let { if (it == min) min + 1f else it }
                val range = max - min
                data.mapIndexed { index, value ->
                    val x = (index / (count - 1).toFloat())
                    val y = 1f - ((value - min) / range)
                    Offset(x, y)
                }
            }
        }

        // Audit 15: Pre-calculate paths outside of draw scope (Zero frame allocation)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val paths = remember(points, density) {
            if (points.size < 2) null
            else {
                // Fixed size for sparklines to ensure stable caching
                val w = 200f // Logic width
                val h = 64f  // Logic height
                val p = with(density) { 2.dp.toPx() }
                val drawW = w - 2 * p
                val drawH = h - 2 * p

                val fill = Path().apply {
                    val startX = p + points.first().x * drawW
                    val startY = p + points.first().y * drawH
                    moveTo(startX, h)
                    lineTo(startX, startY)
                    for (i in 1 until points.size) {
                        val prevX = p + points[i - 1].x * drawW
                        val prevY = p + points[i - 1].y * drawH
                        val currX = p + points[i].x * drawW
                        val currY = p + points[i].y * drawH
                        val cx1 = (prevX + currX) / 2
                        cubicTo(cx1, prevY, cx1, currY, currX, currY)
                    }
                    lineTo(p + points.last().x * drawW, h)
                    close()
                }
                
                val line = Path().apply {
                    val startX = p + points.first().x * drawW
                    val startY = p + points.first().y * drawH
                    moveTo(startX, startY)
                    for (i in 1 until points.size) {
                        val prevX = p + points[i - 1].x * drawW
                        val prevY = p + points[i - 1].y * drawH
                        val currX = p + points[i].x * drawW
                        val currY = p + points[i].y * drawH
                        val cx1 = (prevX + currX) / 2
                        cubicTo(cx1, prevY, cx1, currY, currX, currY)
                    }
                }
                
                val lastX = p + points.last().x * drawW
                val lastY = p + points.last().y * drawH
                
                Triple(fill, line, Offset(lastX, lastY))
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(NavySurface.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        ) {
            val res = paths ?: return@Canvas
            val (fillPath, linePath, lastPoint) = res
            
            // Scaled drawing to fit the actual canvas size
            drawContext.canvas.withSave {
                val scaleX = size.width / 200f
                val scaleY = size.height / 64f
                scale(scaleX, scaleY, Offset.Zero)
                
                drawPath(fillPath, Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
                drawPath(linePath, lineColor, style = Stroke(strokeWidth, cap = StrokeCap.Round))

                if (showDot) {
                    drawCircle(lineColor, radius = 3.dp.toPx() / scaleX, center = lastPoint)
                    drawCircle(lineColor.copy(alpha = 0.3f), radius = 6.dp.toPx() / scaleX, center = lastPoint)
                }
            }
        }
    }
}

/**
 * Latency Sparkline — specialized for ping times (Int ms).
 */
@Composable
fun LatencySparkline(
    history: List<Int>,
    modifier: Modifier = Modifier,
    currentMs: Int = -1
) {
    if (history.isEmpty()) return
    val data = history.map { it.toFloat() }
    val color = when {
        currentMs <= 0 -> TextMuted
        currentMs < 20 -> OnlineGreen
        currentMs < 50 -> CyberTeal
        currentMs < 100 -> WarningAmber
        else -> AlertRed
    }
    Sparkline(
        data = data,
        modifier = modifier,
        lineColor = color,
        label = "Latency",
        valueLabel = if (currentMs > 0) "${currentMs}ms" else "—"
    )
}

/**
 * Activity Sparkline — shows device activity score over time.
 */
@Composable
fun ActivitySparkline(
    history: List<Float>,
    modifier: Modifier = Modifier,
    currentLabel: String = ""
) {
    if (history.isEmpty()) return
    val lastVal = history.lastOrNull() ?: 0f
    val color = when {
        lastVal >= 0.8f -> AlertRed
        lastVal >= 0.5f -> WarningAmber
        lastVal >= 0.2f -> CyberTeal
        else -> OnlineGreen
    }
    Sparkline(
        data = history,
        modifier = modifier,
        lineColor = color,
        label = "Activity",
        valueLabel = currentLabel.ifBlank {
            "${(lastVal * 100).toInt()}%"
        }
    )
}

/**
 * Stability gauge — simple percentage bar with color coding.
 */
@Composable
fun StabilityBar(
    percentage: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        percentage >= 90 -> OnlineGreen
        percentage >= 70 -> CyberTeal
        percentage >= 50 -> WarningAmber
        else -> AlertRed
    }
    val fraction by animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = tween(800),
        label = "stability"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Stability", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text("$percentage%", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(NavyBorder, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
