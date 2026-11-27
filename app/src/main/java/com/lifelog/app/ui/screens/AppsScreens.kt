package com.lifelog.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.data.TimeFormat
import com.lifelog.app.domain.engine.DaySource
import com.lifelog.app.ui.AppDetailViewModel
import com.lifelog.app.ui.AppsPeriod
import com.lifelog.app.ui.AppsSort
import com.lifelog.app.ui.AppsViewModel
import com.lifelog.app.ui.Fmt
import com.lifelog.app.ui.Load
import com.lifelog.app.ui.MainState
import com.lifelog.app.ui.MainViewModel
import com.lifelog.app.ui.components.AppIcon
import com.lifelog.app.ui.components.DailyBars
import com.lifelog.app.ui.components.EmptyState
import com.lifelog.app.ui.components.Eyebrow
import com.lifelog.app.ui.components.KeyValue
import com.lifelog.app.ui.components.MetricTile
import com.lifelog.app.ui.components.ProvenanceLegend
import com.lifelog.app.ui.components.SectionCard
import com.lifelog.app.ui.theme.LocalProvenance
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AppsScreen(vm: AppsViewModel, pad: PaddingValues, iconLoader: (String) -> ImageBitmap?, onApp: (String) -> Unit) {
    val load by vm.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val data = (load as? Load.Ready)?.data
        item {
            OutlinedTextField(
                value = data?.query ?: "", onValueChange = vm::setQuery, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search apps or package names") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppsPeriod.entries.forEach { p -> FilterChip(data?.period == p, { vm.setPeriod(p) }, { Text(p.label) }) }
                Spacer(Modifier.width(8.dp))
                AppsSort.entries.forEach { s -> FilterChip(data?.sort == s, { vm.setSort(s) }, { Text(s.label) }) }
            }
        }
        when (val l = load) {
            Load.Loading -> item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            is Load.Failed -> item { EmptyState("Could not load apps", l.message) }
            is Load.Ready -> {
                if (l.data.rows.isEmpty()) {
                    item { EmptyState("No apps yet", if (l.data.query.isBlank()) "Apps appear once Android returns usage for them." else "No app matches \"${l.data.query}\".") }
                }
                if (l.data.period == AppsPeriod.LIFETIME && l.data.summaryDaysInLifetime > 0) {
                    item {
                        Text(
                            "Lifetime includes ${l.data.summaryDaysInLifetime} days from Android daily summaries.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(l.data.rows, key = { it.packageName }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onApp(r.packageName) }.heightIn(min = 60.dp).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(r.packageName, r.label, iconLoader, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.label ?: r.packageName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    if (r.label != null) append(r.packageName) else append("Label unavailable")
                                    if (!r.installed) append(" · not installed or not visible")
                                    if (r.excluded) append(" · excluded from summaries")
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.duration(r.periodMs), style = MaterialTheme.typography.bodyLarge)
                            Text("lifetime ${Fmt.duration(r.lifetimeMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppDetailScreen(vm: AppDetailViewModel, state: MainState, main: MainViewModel, pad: PaddingValues, iconLoader: (String) -> ImageBitmap?) {
    val load by vm.state.collectAsStateWithLifecycle()
    val p = LocalProvenance.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val l = load) {
            Load.Loading -> item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            is Load.Failed -> item { EmptyState("Could not load app", l.message) }
            is Load.Ready -> {
                val d = l.data
                val excluded = d.packageName in (state.settings?.excludedPackages ?: emptySet())
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(d.packageName, d.app?.displayName, iconLoader, 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.app?.displayName ?: d.packageName, style = MaterialTheme.typography.titleLarge)
                            Text(d.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (d.app?.isCurrentlyInstalled == false) {
                                Text("Not installed, or not visible to LIFELOG", style = MaterialTheme.typography.bodySmall, color = p.partial)
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile("Last 30 days", Fmt.duration(d.last30.perAppMs[d.packageName] ?: 0), "${d.last30.daysWithData} days with data", p.recorded, Modifier.weight(1f))
                        MetricTile("Lifetime stored", Fmt.duration(d.lifetime.perAppMs[d.packageName] ?: 0), d.lifetimeSince?.let { "Since ${Fmt.shortDate(it)}" }, p.historical, Modifier.weight(1f))
                    }
                }
                item {
                    SectionCard {
                        Eyebrow("Daily foreground · last 30 days")
                        Spacer(Modifier.height(12.dp))
                        DailyBars(
                            d.days, { day -> if (day.source == DaySource.NONE) null else day.perAppMs[d.packageName] ?: 0 }, historicalOf = null,
                        )
                        Spacer(Modifier.height(8.dp))
                        ProvenanceLegend()
                    }
                }
                item {
                    SectionCard {
                        Eyebrow("Details")
                        val entries = d.last30.perAppEntries[d.packageName]
                        KeyValue("Foreground entries (30 days)", entries?.toString() ?: "—")
                        Text(
                            "An entry is a foreground start more than 2 s after the app's previous foreground interval ended. It is not a cold-launch count. Days from Android summaries have no entry data.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val tf = state.settings?.timeFormat ?: TimeFormat.SYSTEM
                        KeyValue("First seen", Fmt.dateTime(d.app?.firstSeenAt, tf))
                        KeyValue("Last seen", Fmt.dateTime(d.app?.lastSeenAt, tf))
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Exclude from summaries", style = MaterialTheme.typography.bodyMedium)
                                Text("Hides it from totals. Does not change Android permissions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(excluded, { main.setExcluded(d.packageName, it) })
                        }
                    }
                }
                if (d.monthly.isNotEmpty()) {
                    item {
                        SectionCard {
                            Eyebrow("Android monthly summaries", p.summary)
                            Text("Totals Android retained per month; shown as reported, not merged into LIFELOG days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            val f = DateTimeFormatter.ofPattern("MMM yyyy")
                            d.monthly.take(24).forEach {
                                KeyValue(Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).format(f), Fmt.duration(it.totalMs))
                            }
                        }
                    }
                }
            }
        }
    }
}
