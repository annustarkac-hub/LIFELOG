package com.lifelog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.data.TimeFormat
import com.lifelog.app.ui.Fmt
import com.lifelog.app.ui.Load
import com.lifelog.app.ui.MainState
import com.lifelog.app.ui.TimelineItem
import com.lifelog.app.ui.TimelineType
import com.lifelog.app.ui.TimelineViewModel
import com.lifelog.app.ui.components.EmptyState
import com.lifelog.app.ui.components.SectionCard
import com.lifelog.app.ui.theme.LocalProvenance
import java.time.LocalDate

@Composable
fun TimelineScreen(vm: TimelineViewModel, state: MainState, pad: PaddingValues) {
    val load by vm.state.collectAsStateWithLifecycle()
    val tf = state.settings?.timeFormat ?: TimeFormat.SYSTEM
    var expanded by remember { mutableStateOf<TimelineItem?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val data = (load as? Load.Ready)?.data
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-(data?.days ?: 1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Earlier") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val d = data
                    Text(
                        if (d == null) "" else if (d.days == 1) Fmt.date(d.from) else "${Fmt.shortDate(d.from)} – ${Fmt.shortDate(d.from.plusDays(d.days - 1L))}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(onClick = { vm.shift(data?.days ?: 1) }, enabled = data?.from?.plusDays((data.days).toLong())?.isAfter(LocalDate.now()) == false) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Later")
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(data?.days == 1, { vm.setSpan(1) }, { Text("1 day") })
                FilterChip(data?.days == 7, { vm.setSpan(7) }, { Text("7 days") })
                TextButton(onClick = { vm.setDate(LocalDate.now()) }) { Text("Today") }
                AppFilter(data?.app, data?.appChoices.orEmpty().map { it.packageName to (it.displayName ?: it.packageName) }, vm::setApp)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimelineType.entries.forEach { t ->
                    FilterChip(data?.types?.contains(t) == true, { vm.toggle(t) }, { Text(t.label) }, enabled = data?.app == null || t == TimelineType.APP)
                }
            }
        }
        when (val l = load) {
            Load.Loading -> item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            is Load.Failed -> item { EmptyState("Could not load timeline", l.message) }
            is Load.Ready -> {
                if (l.data.items.isEmpty()) {
                    item {
                        EmptyState(
                            "No supported events stored for this period",
                            "This does not mean nothing happened. Android may not have retained events for this period, or LIFELOG was not running with Usage Access.",
                        )
                    }
                }
                items(l.data.items, key = { "${it.type}-${it.time}-${it.packageName}-${it.end}" }) { item ->
                    TimelineRow(item, tf, expanded == item) { expanded = if (expanded == item) null else item }
                }
            }
        }
    }
}

@Composable
private fun AppFilter(selected: String?, choices: List<Pair<String, String>>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected != null, { open = true }, {
            Text(selected?.let { s -> choices.firstOrNull { it.first == s }?.second ?: s } ?: "All apps", maxLines = 1, overflow = TextOverflow.Ellipsis)
        })
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("All apps") }, { onSelect(null); open = false })
            choices.forEach { (pkg, label) -> DropdownMenuItem({ Text(label) }, { onSelect(pkg); open = false }) }
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, tf: TimeFormat, expanded: Boolean, onClick: () -> Unit) {
    val p = LocalProvenance.current
    val color = when (item.type) {
        TimelineType.APP -> if (item.historical) p.historical else p.recorded
        TimelineType.SCREEN -> p.screen
        TimelineType.LOCK -> MaterialTheme.colorScheme.onSurfaceVariant
        TimelineType.POWER -> p.partial
        TimelineType.BATTERY -> p.summary
    }
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 24.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Fmt.time(item.time, tf) + (item.end?.let { " – ${Fmt.time(it, tf)} · ${Fmt.duration(it - item.time)}" } ?: "") +
                        (if (item.historical) " · historical" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (item.historical) "Returned by Android for a period before LIFELOG tracking started." else "Recorded by LIFELOG after tracking started.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.packageName?.let { Text("Package: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Times are as reported by Android, shown in this phone's current time zone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
