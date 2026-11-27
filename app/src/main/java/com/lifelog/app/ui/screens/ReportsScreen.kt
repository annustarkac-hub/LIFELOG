package com.lifelog.app.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.engine.DaySource
import com.lifelog.app.domain.engine.RangeSummary
import com.lifelog.app.ui.Fmt
import com.lifelog.app.ui.Load
import com.lifelog.app.ui.MainState
import com.lifelog.app.ui.ReportPeriod
import com.lifelog.app.ui.ReportsViewModel
import com.lifelog.app.ui.TopApp
import com.lifelog.app.ui.components.DailyBars
import com.lifelog.app.ui.components.EmptyState
import com.lifelog.app.ui.components.Eyebrow
import com.lifelog.app.ui.components.Heatmap
import com.lifelog.app.ui.components.KeyValue
import com.lifelog.app.ui.components.MetricTile
import com.lifelog.app.ui.components.ProvenanceLegend
import com.lifelog.app.ui.components.SectionCard
import com.lifelog.app.ui.theme.LocalProvenance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(vm: ReportsViewModel, state: MainState, pad: PaddingValues, iconLoader: (String) -> ImageBitmap?) {
    val load by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val p = LocalProvenance.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val data = (load as? Load.Ready)?.data
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { rp ->
                    FilterChip(data?.period == rp, {
                        if (rp == ReportPeriod.CUSTOM) {
                            pickDate(ctx, LocalDate.now().minusDays(6)) { from -> pickDate(ctx, LocalDate.now()) { to -> vm.setCustom(from, to) } }
                        } else vm.setPeriod(rp)
                    }, { Text(rp.label) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }, enabled = data?.period != ReportPeriod.CUSTOM) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous period") }
                Text(
                    data?.let { if (it.from == it.to) Fmt.date(it.from) else "${Fmt.date(it.from)} – ${Fmt.date(it.to)}" } ?: "",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.shift(1) }, enabled = data != null && data.period != ReportPeriod.CUSTOM && data.to.isBefore(LocalDate.now())) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next period")
                }
            }
        }
        when (val l = load) {
            Load.Loading -> item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            is Load.Failed -> item { EmptyState("Could not load report", l.message) }
            is Load.Ready -> {
                val d = l.data
                val s = d.summary
                if (!s.hasData) {
                    item { EmptyState("No data for this period", "Android did not return usage for these dates, or they are before the earliest data on this phone. Gaps are not treated as zero usage.") }
                } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricTile("App foreground", Fmt.duration(s.anyAppForegroundMs), compare(s.anyAppForegroundMs, d.previous.anyAppForegroundMs, d.previous), p.recorded, Modifier.weight(1f))
                            MetricTile("Screen-on", Fmt.duration(s.screenOnMs), compare(s.screenOnMs, d.previous.screenOnMs, d.previous), p.screen, Modifier.weight(1f))
                        }
                    }
                    item {
                        SectionCard {
                            Eyebrow("Coverage")
                            KeyValue("Days with data", "${s.daysWithData} of ${s.days.size}")
                            KeyValue("Partial days", s.partialDays.toString())
                            KeyValue("Android-summary-only days", s.summaryOnlyDays.toString())
                            KeyValue("Recorded / historical (app)", "${Fmt.duration(s.recordedAppMs)} / ${Fmt.duration(s.historicalAppMs)}")
                            if (s.summaryOnlyDays > 0) {
                                Text(
                                    "Summary-only days: Android per-app totals ${Fmt.duration(s.summaryPerAppSumMs)} (may overlap between apps; not included in the app-foreground total above).",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (d.days.size in 2..62) {
                        item {
                            SectionCard {
                                Eyebrow("Trend · app foreground per day")
                                Spacer(Modifier.height(12.dp))
                                DailyBars(d.days, { it.anyAppForegroundMs ?: if (it.source == DaySource.ANDROID_DAILY_SUMMARY) it.summaryPerAppSumMs else null })
                                Spacer(Modifier.height(8.dp))
                                ProvenanceLegend()
                            }
                        }
                        item {
                            SectionCard {
                                Eyebrow("Trend · screen-on per day")
                                Spacer(Modifier.height(12.dp))
                                DailyBars(d.days, { it.screenOnMs }, historicalOf = { it.historicalScreenMs })
                            }
                        }
                    } else if (d.days.size > 62) {
                        item {
                            SectionCard {
                                Eyebrow("Months")
                                d.days.groupBy { it.date.withDayOfMonth(1) }.forEach { (m, days) ->
                                    val sm = com.lifelog.app.domain.engine.Analytics.summarize(days)
                                    KeyValue(m.format(DateTimeFormatter.ofPattern("MMMM yyyy")), if (sm.hasData) "${Fmt.duration(sm.anyAppForegroundMs)} · ${sm.daysWithData}d" else "no data")
                                }
                            }
                        }
                    }
                    item {
                        SectionCard {
                            Eyebrow("App distribution")
                            Spacer(Modifier.height(8.dp))
                            val top = s.perAppMs.entries.sortedByDescending { it.value }.take(8).map { TopApp(it.key, d.labels[it.key]?.displayName, it.value) }
                            TopList(top, iconLoader) {}
                        }
                    }
                    item {
                        SectionCard {
                            Eyebrow("When you use your phone")
                            Text("Recorded app sessions by weekday and hour", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Heatmap(d.heatmap)
                        }
                    }
                }
                if (d.yearlyScreen.isNotEmpty() || d.yearlyApps.isNotEmpty()) {
                    item {
                        SectionCard {
                            Eyebrow("Android yearly & monthly summaries", p.summary)
                            Text(
                                "Coarse totals Android itself retained. Bucket boundaries are Android's; these are shown as reported and are not merged into the figures above.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val zone = ZoneId.systemDefault()
                            val f = DateTimeFormatter.ofPattern("d MMM yyyy")
                            fun span(a: Long, b: Long) = "${Instant.ofEpochMilli(a).atZone(zone).format(f)} – ${Instant.ofEpochMilli(b).atZone(zone).format(f)}"
                            d.yearlyApps.groupBy { it.start }.toSortedMap(compareByDescending { it }).forEach { (start, rows) ->
                                val end = rows.maxOf { it.end }
                                val screen = d.yearlyScreen.firstOrNull { it.start == start }
                                KeyValue(span(start, end), "Screen ${Fmt.duration(screen?.totalMs)} · ${rows.size} apps")
                            }
                            if (d.monthlyScreen.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Eyebrow("Monthly screen-interactive")
                                d.monthlyScreen.take(12).forEach { KeyValue(span(it.start, it.end), Fmt.duration(it.totalMs)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun compare(cur: Long, prev: Long, prevRange: RangeSummary): String =
    if (!prevRange.hasData || prev == 0L) "No previous-period data to compare"
    else {
        val pct = ((cur - prev) * 100.0 / prev)
        "${if (pct >= 0) "+" else ""}${"%.0f".format(pct)}% vs previous period"
    }

private fun pickDate(ctx: android.content.Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}
