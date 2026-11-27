package com.lifelog.app.data

import com.lifelog.app.data.db.AppIdentityEntity
import com.lifelog.app.data.db.BatterySnapshotEntity
import com.lifelog.app.data.db.IntervalRow
import com.lifelog.app.data.db.SystemEventEntity
import com.lifelog.app.domain.CoverageKind
import com.lifelog.app.domain.Interval
import com.lifelog.app.domain.MetricType
import com.lifelog.app.domain.engine.Analytics
import com.lifelog.app.domain.engine.AnalyticsInput
import com.lifelog.app.domain.engine.Bucket
import com.lifelog.app.domain.engine.DaySummary
import com.lifelog.app.domain.engine.EventInterval
import com.lifelog.app.domain.engine.IntervalMath
import com.lifelog.app.domain.engine.RangeSummary
import com.lifelog.app.platform.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AndroidSummaryRow(val packageName: String?, val start: Long, val end: Long, val totalMs: Long)

/** Read-only queries for the UI. Everything is computed from stored rows; nothing is generated. */
class AnalyticsService(private val repo: LifelogRepository, private val clock: Clock) {

    suspend fun range(from: LocalDate, toInclusive: LocalDate, zone: ZoneId, excluded: Set<String>, pkg: String? = null): RangeSummary =
        Analytics.summarize(days(from, toInclusive, zone, excluded, pkg))

    suspend fun days(from: LocalDate, toInclusive: LocalDate, zone: ZoneId, excluded: Set<String>, pkg: String? = null): List<DaySummary> =
        withContext(Dispatchers.IO) {
            val start = IntervalMath.dayBounds(from, zone).start
            val end = IntervalMath.dayBounds(toInclusive, zone).end
            // Widen bucket query by a day each side: buckets are attributed by midpoint.
            val bStart = start - DAY
            val bEnd = end + DAY
            suspend fun iv(metric: MetricType, f: Long, t: Long): List<IntervalRow> =
                if (pkg == null) repo.usage.intervals(metric.name, f, t) else repo.usage.intervalsForPackage(metric.name, pkg, f, t)
            val input = AnalyticsInput(
                zone = zone,
                now = clock.now(),
                firstTrackingAt = repo.profile()?.firstLifelogTrackingAtUtc,
                appSessions = iv(MetricType.APP_FOREGROUND, start, end).map { EventInterval(it.packageName, Interval(it.start, it.end), it.isHistorical) },
                screenIntervals = if (pkg != null) emptyList() else repo.usage.intervals(MetricType.DEVICE_SCREEN_ON.name, start, end)
                    .map { EventInterval(null, Interval(it.start, it.end), it.isHistorical) },
                appDailyBuckets = iv(MetricType.API_APP_DAILY, bStart, bEnd).map { Bucket(it.packageName, it.start, it.end, it.durationMs) },
                screenDailyBuckets = if (pkg != null) emptyList() else repo.usage.intervals(MetricType.API_SCREEN_DAILY.name, bStart, bEnd)
                    .map { Bucket(null, it.start, it.end, it.durationMs) },
                eventCoverage = repo.coverage.allOfKind(CoverageKind.EVENTS.name).map { Interval(it.intervalStartUtc, it.intervalEndUtc) },
                notGranted = repo.coverage.allOfKind(CoverageKind.NOT_GRANTED.name).map { Interval(it.intervalStartUtc, it.intervalEndUtc) },
                excludedPackages = excluded,
            )
            Analytics.days(input, from, toInclusive)
        }

    /** Earliest local date that has any stored usage (events or Android daily buckets). */
    suspend fun earliestDataDate(zone: ZoneId): LocalDate? = withContext(Dispatchers.IO) {
        val e = repo.usage.earliestStart(
            listOf(MetricType.APP_FOREGROUND.name, MetricType.DEVICE_SCREEN_ON.name, MetricType.API_APP_DAILY.name, MetricType.API_SCREEN_DAILY.name)
        )
        val c = repo.coverage.earliestStart(CoverageKind.EVENTS.name)
        listOfNotNull(e, c).minOrNull()?.let { IntervalMath.localDate(it, zone) }
    }

    suspend fun apps(): Map<String, AppIdentityEntity> = withContext(Dispatchers.IO) { repo.meta.allApps().associateBy { it.packageName } }

    suspend fun sessions(from: Long, to: Long, pkg: String? = null): List<IntervalRow> = withContext(Dispatchers.IO) {
        if (pkg == null) repo.usage.intervals(MetricType.APP_FOREGROUND.name, from, to)
        else repo.usage.intervalsForPackage(MetricType.APP_FOREGROUND.name, pkg, from, to)
    }

    suspend fun systemEvents(from: Long, to: Long): List<SystemEventEntity> = withContext(Dispatchers.IO) { repo.events.inRange(from, to) }

    suspend fun battery(from: Long, to: Long): List<BatterySnapshotEntity> = withContext(Dispatchers.IO) { repo.battery.inRange(from, to) }

    /** Android-retained monthly/yearly summaries, shown separately from LIFELOG day totals. */
    suspend fun androidSummaries(metric: MetricType): List<AndroidSummaryRow> = withContext(Dispatchers.IO) {
        repo.usage.allOfMetric(metric.name).map { AndroidSummaryRow(it.packageName, it.startAtUtc, it.endAtUtc, it.durationMs) }
    }

    /** Hour-of-day × day-of-week minutes from event sessions only (index: [dayOfWeek 0=Mon][hour]). */
    suspend fun heatmap(from: LocalDate, toInclusive: LocalDate, zone: ZoneId, excluded: Set<String>): Array<LongArray> = withContext(Dispatchers.IO) {
        val start = IntervalMath.dayBounds(from, zone).start
        val end = IntervalMath.dayBounds(toInclusive, zone).end
        val rows = repo.usage.intervals(MetricType.APP_FOREGROUND.name, start, end).filter { it.packageName !in excluded }
        val union = IntervalMath.union(rows.map { Interval(maxOf(it.start, start), minOf(it.end, end)) }.filter { it.end > it.start })
        val grid = Array(7) { LongArray(24) }
        for (iv in union) {
            var t = iv.start
            while (t < iv.end) {
                val z = java.time.Instant.ofEpochMilli(t).atZone(zone)
                val hourEnd = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val segEnd = minOf(iv.end, hourEnd)
                grid[z.dayOfWeek.value - 1][z.hour] += segEnd - t
                t = segEnd
            }
        }
        grid
    }

    companion object {
        private const val DAY = 86_400_000L
    }
}
