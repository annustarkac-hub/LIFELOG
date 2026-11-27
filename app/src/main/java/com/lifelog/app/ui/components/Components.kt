package com.lifelog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.CoverageStatus
import com.lifelog.app.domain.engine.DaySource
import com.lifelog.app.domain.engine.DaySummary
import com.lifelog.app.ui.Fmt
import com.lifelog.app.ui.theme.LocalProvenance
import com.lifelog.app.ui.theme.MetricStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SectionCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().let { if (onClick != null) it.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick) else it }
    Card(
        modifier = m,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

/** A metric tile. [value] null renders an explicit "no data" dash, never a zero. */
@Composable
fun MetricTile(label: String, value: String, caption: String?, accent: Color, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Eyebrow(label)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MetricStyle, color = MaterialTheme.colorScheme.onSurface)
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CoverageChip(status: CoverageStatus, modifier: Modifier = Modifier) {
    val p = LocalProvenance.current
    val color = when (status) {
        CoverageStatus.AVAILABLE -> p.recorded
        CoverageStatus.PARTIAL -> p.partial
        CoverageStatus.NOT_GRANTED -> MaterialTheme.colorScheme.error
        CoverageStatus.UNAVAILABLE, CoverageStatus.UNKNOWN -> p.gap
    }
    Row(
        modifier.clip(RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(Fmt.coverage(status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LegendDot(color: Color, label: String, hatched: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp, bottom = 4.dp)) {
        Canvas(Modifier.size(10.dp)) {
            if (hatched) drawRoundRect(color, style = Stroke(2f), cornerRadius = CornerRadius(3f))
            else drawRoundRect(color, cornerRadius = CornerRadius(3f))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProvenanceLegend() {
    val p = LocalProvenance.current
    androidx.compose.foundation.layout.FlowRow {
        LegendDot(p.recorded, "Recorded by LIFELOG")
        LegendDot(p.historical, "Historical (returned by Android)")
        LegendDot(p.summary, "Android daily summary")
        LegendDot(p.gap, "Data unavailable", hatched = true)
    }
}

/**
 * Daily bar chart. Each bar is split into recorded / historical segments; Android-summary days use the summary
 * colour; days without data render as an outlined "gap" bar, so missing data is never shown as zero usage.
 */
@Composable
fun DailyBars(
    days: List<DaySummary>,
    valueOf: (DaySummary) -> Long?,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    /** Historical part of the bar; null draws the whole bar in the recorded colour. */
    historicalOf: ((DaySummary) -> Long)? = { it.historicalAppMs },
) {
    val p = LocalProvenance.current
    val max = (days.maxOfOrNull { valueOf(it) ?: 0 } ?: 0).coerceAtLeast(60_000)
    val desc = days.joinToString { "${Fmt.shortDate(it.date)} ${Fmt.duration(valueOf(it))}" }
    Column(modifier.semantics { contentDescription = "Daily chart: $desc" }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (days.isEmpty()) return@Canvas
            val slot = size.width / days.size
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            days.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2
                val v = valueOf(d)
                if (d.source == DaySource.NONE || v == null) {
                    drawRoundRect(p.gap, Offset(x, size.height - 10.dp.toPx()), Size(barW, 10.dp.toPx()), CornerRadius(4f), style = Stroke(1.5f))
                    return@forEachIndexed
                }
                val h = (v.toFloat() / max) * (size.height - 4)
                if (d.source == DaySource.ANDROID_DAILY_SUMMARY || historicalOf == null) {
                    val c = if (d.source == DaySource.ANDROID_DAILY_SUMMARY) p.summary else p.recorded
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), CornerRadius(6f))
                } else {
                    val histH = h * (historicalOf(d).toFloat() / v.coerceAtLeast(1)).coerceIn(0f, 1f)
                    drawRoundRect(p.recorded, Offset(x, size.height - h), Size(barW, h - histH), CornerRadius(6f))
                    if (histH > 0) drawRoundRect(p.historical, Offset(x, size.height - histH), Size(barW, histH), CornerRadius(6f))
                }
                if (d.coverage == CoverageStatus.PARTIAL) {
                    drawCircle(p.partial, 3.dp.toPx(), Offset(x + barW / 2, size.height - h - 6.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            val step = when {
                days.size <= 7 -> 1
                days.size <= 14 -> 2
                else -> days.size / 5
            }.coerceAtLeast(1)
            days.forEachIndexed { i, d ->
                Text(
                    if (i % step == 0) (if (days.size <= 7) Fmt.weekday(d.date) else Fmt.shortDate(d.date)) else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** Horizontal share bar for top-app lists. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun Heatmap(grid: Array<LongArray>, modifier: Modifier = Modifier) {
    val p = LocalProvenance.current
    val max = grid.maxOf { row -> row.max() }.coerceAtLeast(1)
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val base = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.semantics { contentDescription = "Usage heatmap by weekday and hour, recorded sessions only" }) {
        grid.forEachIndexed { r, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(labels[r], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    val w = size.width / 24
                    row.forEachIndexed { h, v ->
                        val a = (v.toFloat() / max)
                        val c = if (v == 0L) base else p.recorded.copy(alpha = 0.18f + 0.82f * a)
                        drawRoundRect(c, Offset(h * w + 1, 1f), Size(w - 2, size.height - 2), CornerRadius(3f))
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
        }
        Row(Modifier.padding(start = 34.dp)) {
            listOf("0", "6", "12", "18", "23").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp)); action()
        }
    }
}

@Composable
fun NoticeBanner(title: String, body: String, color: Color, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(10.dp)); action()
        }
    }
}

/** App icon loaded off the main thread through the injected loader; falls back to an initial. */
@Composable
fun AppIcon(packageName: String, label: String?, loader: (String) -> ImageBitmap?, size: Dp = 36.dp) {
    val bmp by produceState<ImageBitmap?>(null, packageName) { value = withContext(Dispatchers.IO) { loader(packageName) } }
    val b = bmp
    if (b != null) {
        Image(b, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text((label ?: packageName).take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1.2f))
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

val ScreenSpacing = Arrangement.spacedBy(12.dp)
