package com.lifelog.app.domain.engine

import com.lifelog.app.domain.CoverageStatus
import com.lifelog.app.domain.Interval
import java.time.LocalDate
import java.time.ZoneId

/** Where a day's numbers come from. Never mixed within one metric of one day. */
enum class DaySource {
    /** Reconstructed from usage events (session detail available). */
    EVENTS,

    /** Android daily aggregate only (no session detail). Always historical. */
    ANDROID_DAILY_SUMMARY,

    /** Nothing stored. */
    NONE,
}

data class EventInterval(val packageName: String?, val interval: Interval, val isHistorical: Boolean)
data class Bucket(val packageName: String?, val start: Long, val end: Long, val totalMs: Long)

data class AnalyticsInput(
    val zone: ZoneId,
    val now: Long,
    val firstTrackingAt: Long?,
    val appSessions: List<EventInterval>,
    val screenIntervals: List<EventInterval>,
    val appDailyBuckets: List<Bucket>,
    val screenDailyBuckets: List<Bucket>,
    val eventCoverage: List<Interval>,
    val notGranted: List<Interval>,
    val excludedPackages: Set<String> = emptySet(),
)

data class DaySummary(
    val date: LocalDate,
    val source: DaySource,
    val coverage: CoverageStatus,
    /** Fraction of the elapsed part of the day with event coverage (0..1). */
    val coveredFraction: Double,
    /** Android-reported screen-interactive time; null if no supported source for this day. */
    val screenOnMs: Long?,
    /** Union of all (non-excluded) app foreground intervals: time with ANY app in foreground. Null on summary days. */
    val anyAppForegroundMs: Long?,
    val perAppMs: Map<String, Long>,
    val perAppEntries: Map<String, Int>,
    /** Portion of the day's app foreground (any-app union, or bucket sum on summary days) that is historical. */
    val historicalAppMs: Long,
    val recordedAppMs: Long,
    val historicalScreenMs: Long,
    val recordedScreenMs: Long,
    /** Summary days only: sum of Android per-app daily totals. May overlap (split screen); never shown as device time. */
    val summaryPerAppSumMs: Long = 0,
)

data class RangeSummary(
    val days: List<DaySummary>,
    val screenOnMs: Long,
    val anyAppForegroundMs: Long,
    val perAppMs: Map<String, Long>,
    val perAppEntries: Map<String, Int>,
    val historicalAppMs: Long,
    val recordedAppMs: Long,
    val historicalScreenMs: Long,
    val recordedScreenMs: Long,
    val daysWithData: Int,
    val partialDays: Int,
    val summaryOnlyDays: Int,
    val summaryPerAppSumMs: Long,
) {
    val hasData get() = daysWithData > 0
}

/**
 * Day-by-day analytics. Guarantees:
 *  - Screen-on comes only from screen intervals/buckets, never from app usage.
 *  - Per-app totals are per-package interval unions; "any app" is a union across packages (never a sum).
 *  - A day with any event coverage uses events only; Android daily buckets are used only for days without it.
 *  - Range totals are sums of disjoint days, so nothing is counted twice.
 */
object Analytics {

    fun days(input: AnalyticsInput, from: LocalDate, toInclusive: LocalDate): List<DaySummary> {
        val zone = input.zone
        val apps = input.appSessions.filter { it.packageName != null && it.packageName !in input.excludedPackages }
        val appBuckets = input.appDailyBuckets.filter { it.packageName != null && it.packageName !in input.excludedPackages }
        val bucketsByDay = appBuckets.groupBy { bucketDay(it, zone) }
        val screenBucketsByDay = input.screenDailyBuckets.groupBy { bucketDay(it, zone) }
        val coverageUnion = IntervalMath.union(input.eventCoverage)
        val out = ArrayList<DaySummary>()
        var d = from
        while (!d.isAfter(toInclusive)) {
            out += day(d, input, apps, coverageUnion, bucketsByDay[d].orEmpty(), screenBucketsByDay[d].orEmpty())
            d = d.plusDays(1)
        }
        return out
    }

    fun summarize(days: List<DaySummary>): RangeSummary {
        val perApp = HashMap<String, Long>()
        val entries = HashMap<String, Int>()
        days.forEach { day ->
            day.perAppMs.forEach { (k, v) -> perApp[k] = (perApp[k] ?: 0) + v }
            day.perAppEntries.forEach { (k, v) -> entries[k] = (entries[k] ?: 0) + v }
        }
        return RangeSummary(
            days = days,
            screenOnMs = days.sumOf { it.screenOnMs ?: 0 },
            anyAppForegroundMs = days.sumOf { it.anyAppForegroundMs ?: 0 },
            perAppMs = perApp,
            perAppEntries = entries,
            historicalAppMs = days.sumOf { it.historicalAppMs },
            recordedAppMs = days.sumOf { it.recordedAppMs },
            historicalScreenMs = days.sumOf { it.historicalScreenMs },
            recordedScreenMs = days.sumOf { it.recordedScreenMs },
            daysWithData = days.count { it.source != DaySource.NONE },
            partialDays = days.count { it.coverage == CoverageStatus.PARTIAL },
            summaryOnlyDays = days.count { it.source == DaySource.ANDROID_DAILY_SUMMARY },
            summaryPerAppSumMs = days.sumOf { it.summaryPerAppSumMs },
        )
    }

    /** Android bucket boundaries are not guaranteed to be local-midnight aligned; attribute by bucket midpoint. */
    fun bucketDay(b: Bucket, zone: ZoneId): LocalDate = IntervalMath.localDate(b.start + (b.end - b.start) / 2, zone)

    private fun day(
        date: LocalDate,
        input: AnalyticsInput,
        apps: List<EventInterval>,
        coverage: List<Interval>,
        appBuckets: List<Bucket>,
        screenBuckets: List<Bucket>,
    ): DaySummary {
        val full = IntervalMath.dayBounds(date, input.zone)
        val elapsedEnd = minOf(full.end, input.now)
        if (elapsedEnd <= full.start) {
            return empty(date, CoverageStatus.UNKNOWN)
        }
        val bounds = Interval(full.start, elapsedEnd)
        val covered = IntervalMath.coveredLength(coverage, bounds)
        val fraction = covered.toDouble() / bounds.length

        if (covered > 0) {
            val dayApps = apps.mapNotNull { e -> e.interval.intersect(bounds)?.let { e.copy(interval = it) } }
            val byPkg = dayApps.groupBy { it.packageName!! }
            val perApp = byPkg.mapValues { (_, v) -> IntervalMath.totalLength(v.map { it.interval }) }.filterValues { it > 0 }
            val entries = byPkg.mapValues { (_, v) -> EventReconstructor.countEntries(v.map { it.interval }) }
            val anyApp = IntervalMath.union(dayApps.map { it.interval })
            val tracking = input.firstTrackingAt
            val histApp = if (tracking == null) 0L else IntervalMath.coveredLength(anyApp, Interval(bounds.start, maxOf(bounds.start, minOf(bounds.end, tracking))))
            val dayScreen = IntervalMath.union(input.screenIntervals.mapNotNull { it.interval.intersect(bounds) })
            val screenTotal = dayScreen.sumOf { it.length }
            val histScreen = if (tracking == null) 0L else IntervalMath.coveredLength(dayScreen, Interval(bounds.start, maxOf(bounds.start, minOf(bounds.end, tracking))))
            val anyTotal = anyApp.sumOf { it.length }
            return DaySummary(
                date = date, source = DaySource.EVENTS,
                coverage = if (fraction >= 0.999) CoverageStatus.AVAILABLE else CoverageStatus.PARTIAL,
                coveredFraction = fraction, screenOnMs = screenTotal, anyAppForegroundMs = anyTotal,
                perAppMs = perApp, perAppEntries = entries,
                historicalAppMs = histApp, recordedAppMs = anyTotal - histApp,
                historicalScreenMs = histScreen, recordedScreenMs = screenTotal - histScreen,
            )
        }

        if (appBuckets.isNotEmpty() || screenBuckets.isNotEmpty()) {
            val perApp = appBuckets.groupBy { it.packageName!! }.mapValues { (_, v) -> v.sumOf { it.totalMs } }.filterValues { it > 0 }
            val screen = if (screenBuckets.isEmpty()) null else screenBuckets.sumOf { it.totalMs }
            return DaySummary(
                date = date, source = DaySource.ANDROID_DAILY_SUMMARY, coverage = CoverageStatus.AVAILABLE,
                coveredFraction = 0.0, screenOnMs = screen,
                // Bucket totals are per app; a union across apps cannot be computed, so no "any app" value is claimed.
                anyAppForegroundMs = null,
                perAppMs = perApp, perAppEntries = emptyMap(),
                historicalAppMs = 0, recordedAppMs = 0,
                historicalScreenMs = screen ?: 0, recordedScreenMs = 0,
                summaryPerAppSumMs = perApp.values.sum(),
            )
        }

        val status = when {
            input.notGranted.any { it.overlaps(bounds) } -> CoverageStatus.NOT_GRANTED
            coverage.isEmpty() || bounds.end <= coverage.first().start -> CoverageStatus.UNKNOWN
            else -> CoverageStatus.UNAVAILABLE
        }
        return empty(date, status)
    }

    private fun empty(date: LocalDate, status: CoverageStatus) = DaySummary(
        date, DaySource.NONE, status, 0.0, null, null, emptyMap(), emptyMap(), 0, 0, 0, 0,
    )
}
