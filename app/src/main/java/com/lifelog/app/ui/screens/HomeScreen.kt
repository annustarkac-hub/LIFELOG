package com.lifelog.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.collect.CollectionOutcome
import com.lifelog.app.data.TimeFormat
import com.lifelog.app.domain.CoverageStatus
import com.lifelog.app.domain.engine.DaySource
import com.lifelog.app.ui.Fmt
import com.lifelog.app.ui.HomeViewModel
import com.lifelog.app.ui.Load
import com.lifelog.app.ui.MainState
import com.lifelog.app.ui.TopApp
import com.lifelog.app.ui.components.AppIcon
import com.lifelog.app.ui.components.CoverageChip
import com.lifelog.app.ui.components.DailyBars
import com.lifelog.app.ui.components.EmptyState
import com.lifelog.app.ui.components.Eyebrow
import com.lifelog.app.ui.components.MetricTile
import com.lifelog.app.ui.components.NoticeBanner
import com.lifelog.app.ui.components.ProvenanceLegend
import com.lifelog.app.ui.components.SectionCard
import com.lifelog.app.ui.components.ShareBar
import com.lifelog.app.ui.openUsageAccessSettings
import com.lifelog.app.ui.theme.LocalProvenance

@Composable
fun StatusBanners(state: MainState, onCollect: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalProvenance.current
    if (state.settings?.trackingEnabled == false) {
        NoticeBanner("Tracking is paused", "LIFELOG is not collecting. Existing data is kept. Turn tracking on in Privacy & permissions.", p.partial)
    } else if (!state.hasUsageAccess) {
        NoticeBanner(
            "Usage Access is off — collection paused",
            "Your recorded data is safe. Periods without access are shown as gaps. Android may still return recent events once access is restored.",
            MaterialTheme.colorScheme.error,
        ) { TextButton(onClick = { openUsageAccessSettings(ctx) }) { Text("Open Usage Access settings") } }
    } else if (state.lastOutcome == CollectionOutcome.AndroidReturnedNoData) {
        NoticeBanner("Android returned no data", "This can happen while the phone is locked after a restart. Unlock and try again.", p.partial) {
            TextButton(onClick = onCollect) { Text("Retry collection") }
        }
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    state: MainState,
    pad: PaddingValues,
    iconLoader: (String) -> ImageBitmap?,
    onApp: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onGrant: () -> Unit,
    onCollect: () -> Unit,
    onCoverage: () -> Unit,
) {
    val load by vm.state.collectAsStateWithLifecycle()
    val p = LocalProvenance.current
    val tf = state.settings?.timeFormat ?: TimeFormat.SYSTEM
    val now = System.currentTimeMillis()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusBanners(state, onCollect) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Last successful collection")
                    Text(
                        "${Fmt.dateTime(state.checkpoint?.lastSuccessfulRunAtUtc, tf)} · ${Fmt.relative(state.checkpoint?.lastSuccessfulRunAtUtc, now)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onCollect, enabled = !state.collecting, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.collecting) "Collecting…" else "Collect now")
                }
            }
        }
        when (val l = load) {
            Load.Loading -> item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            is Load.Failed -> item { EmptyState("Could not load dashboard", l.message) }
            is Load.Ready -> {
                val d = l.data
                val today = d.today
                item {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(
                            "Screen-on today", Fmt.duration(today.screenOnMs),
                            if (today.screenOnMs == null) "No supported screen data" else "Android-reported interactive time",
                            p.screen, Modifier.weight(1f).fillMaxHeight(),
                        )
                        MetricTile(
                            "App foreground today", Fmt.duration(today.anyAppForegroundMs),
                            "Time with any app in front", p.recorded, Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                item {
                    SectionCard(onClick = onCoverage) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow("Coverage today")
                                Text(
                                    if (today.coverage == CoverageStatus.PARTIAL) "${(today.coveredFraction * 100).toInt()}% of today has event data" else Fmt.coverage(today.coverage),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            CoverageChip(today.coverage)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("A session still open when LIFELOG last ran is counted after it ends.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    SectionCard {
                        Eyebrow("Last 7 days · app foreground")
                        Spacer(Modifier.height(4.dp))
                        Text(Fmt.duration(d.last7.anyAppForegroundMs), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Screen-on ${Fmt.duration(d.last7.screenOnMs)} · ${d.last7.daysWithData}/7 days with data" +
                                (if (d.last7.partialDays > 0) " · ${d.last7.partialDays} partial" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        DailyBars(d.last7.days, { it.anyAppForegroundMs ?: if (it.source == DaySource.ANDROID_DAILY_SUMMARY) it.summaryPerAppSumMs else null })
                        Spacer(Modifier.height(10.dp))
                        ProvenanceLegend()
                    }
                }
                item {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile("Last 30 days", Fmt.duration(d.last30.anyAppForegroundMs), "${d.last30.daysWithData}/30 days with data", p.recorded, Modifier.weight(1f).fillMaxHeight())
                        MetricTile(
                            "Recorded since tracking began", Fmt.duration(d.lifetime?.recordedAppMs),
                            state.profile?.firstLifelogTrackingAtUtc?.let { "Since ${Fmt.dateTime(it, tf)}" } ?: "Tracking not started",
                            p.recorded, Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                item {
                    SectionCard {
                        Eyebrow("Historical data available", p.historical)
                        Spacer(Modifier.height(6.dp))
                        val life = d.lifetime
                        if (life == null || !life.hasData) {
                            Text("Android has not returned any history yet.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("Earliest data: ${d.lifetimeSince?.let { Fmt.date(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Historical app foreground from events: ${Fmt.duration(life.historicalAppMs)}" +
                                    (if (life.summaryOnlyDays > 0) "\n${life.summaryOnlyDays} older days from Android daily summaries (per-app totals only)" else ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "History before this point is not available from Android on this phone.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    SectionCard {
                        Eyebrow("Most-used apps · today")
                        Spacer(Modifier.height(8.dp))
                        TopList(d.topToday.ifEmpty { d.top7 }, iconLoader, onApp)
                        if (d.topToday.isEmpty() && d.top7.isNotEmpty()) {
                            Text("No app sessions recorded today yet — showing last 7 days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (d.top7.isEmpty()) Text("No app sessions recorded yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun TopList(apps: List<TopApp>, iconLoader: (String) -> ImageBitmap?, onApp: (String) -> Unit) {
    val max = apps.maxOfOrNull { it.ms } ?: 1
    val p = LocalProvenance.current
    apps.forEach { a ->
        Row(
            Modifier.fillMaxWidth().clickable { onApp(a.packageName) }.heightIn(min = 52.dp).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(a.packageName, a.label, iconLoader)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(a.label ?: a.packageName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Fmt.duration(a.ms), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                ShareBar(a.ms.toFloat() / max, p.recorded)
            }
        }
    }
}
