package com.lifelog.app.engine

import com.lifelog.app.domain.CoverageStatus
import com.lifelog.app.domain.Interval
import com.lifelog.app.domain.engine.Analytics
import com.lifelog.app.domain.engine.AnalyticsInput
import com.lifelog.app.domain.engine.Bucket
import com.lifelog.app.domain.engine.DaySource
import com.lifelog.app.domain.engine.EventInterval
import com.lifelog.app.domain.engine.IntervalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val H = 3_600_000L
    private val d1 = LocalDate.of(2026, 11, 20)
    private val d1s = IntervalMath.dayBounds(d1, zone).start
    private val d2 = d1.plusDays(1)
    private val d2s = d1s + 24 * H

    private fun input(
        apps: List<EventInterval> = emptyList(), screen: List<EventInterval> = emptyList(),
        appBuckets: List<Bucket> = emptyList(), screenBuckets: List<Bucket> = emptyList(),
        coverage: List<Interval> = listOf(Interval(d1s, d1s + 72 * H)), notGranted: List<Interval> = emptyList(),
        firstTracking: Long? = d1s, now: Long = d1s + 72 * H, excluded: Set<String> = emptySet(),
    ) = AnalyticsInput(zone, now, firstTracking, apps, screen, appBuckets, screenBuckets, coverage, notGranted, excluded)

    private fun app(p: String, s: Long, e: Long, hist: Boolean = false) = EventInterval(p, Interval(s, e), hist)

    @Test fun overlappingRecordsOfSameAppAreNotDoubleCounted() {
        // Same session stored twice by overlapping collection windows, plus a slightly different reconstruction.
        val i = input(apps = listOf(app("a", d1s + H, d1s + 2 * H), app("a", d1s + H, d1s + 2 * H), app("a", d1s + H + 60_000, d1s + 2 * H)))
        val d = Analytics.days(i, d1, d1).single()
        assertEquals(H, d.perAppMs["a"])
        assertEquals(H, d.anyAppForegroundMs)
    }

    @Test fun anyAppIsUnionAcrossAppsNotSum() {
        // Split-screen: two apps in foreground over the same hour.
        val i = input(apps = listOf(app("a", d1s, d1s + H), app("b", d1s, d1s + H)))
        val d = Analytics.days(i, d1, d1).single()
        assertEquals(H, d.perAppMs["a"]); assertEquals(H, d.perAppMs["b"])
        assertEquals(H, d.anyAppForegroundMs)
    }

    @Test fun screenOnIsNeverDerivedFromAppUsage() {
        val i = input(apps = listOf(app("a", d1s, d1s + 3 * H)), screen = listOf(EventInterval(null, Interval(d1s, d1s + H), false)))
        val d = Analytics.days(i, d1, d1).single()
        assertEquals(H, d.screenOnMs)
        assertEquals(3 * H, d.anyAppForegroundMs)
    }

    @Test fun sessionAcrossMidnightSplitsBetweenDays() {
        val i = input(apps = listOf(app("a", d2s - H, d2s + H)))
        val days = Analytics.days(i, d1, d2)
        assertEquals(H, days[0].perAppMs["a"]); assertEquals(H, days[1].perAppMs["a"])
        assertEquals(2 * H, Analytics.summarize(days).anyAppForegroundMs)
    }

    @Test fun bucketsUsedOnlyOnDaysWithoutEventCoverage() {
        val i = input(
            apps = listOf(app("a", d2s + H, d2s + 2 * H)),
            appBuckets = listOf(Bucket("a", d1s, d1s + 24 * H, 5 * H), Bucket("a", d2s, d2s + 24 * H, 9 * H)),
            screenBuckets = listOf(Bucket(null, d1s, d1s + 24 * H, 4 * H)),
            coverage = listOf(Interval(d2s, d2s + 24 * H)),
        )
        val days = Analytics.days(i, d1, d2)
        assertEquals(DaySource.ANDROID_DAILY_SUMMARY, days[0].source)
        assertEquals(5 * H, days[0].perAppMs["a"]); assertEquals(4 * H, days[0].screenOnMs)
        assertNull("no union claim on summary days", days[0].anyAppForegroundMs)
        assertEquals(DaySource.EVENTS, days[1].source)
        assertEquals("bucket ignored where events exist", H, days[1].perAppMs["a"])
        val s = Analytics.summarize(days)
        assertEquals(H, s.anyAppForegroundMs)
        assertEquals(5 * H, s.summaryPerAppSumMs)
    }

    @Test fun historicalAndRecordedAreSeparated() {
        val tracking = d1s + 10 * H
        val i = input(apps = listOf(app("a", d1s + 8 * H, d1s + 12 * H)), firstTracking = tracking)
        val d = Analytics.days(i, d1, d1).single()
        assertEquals(2 * H, d.historicalAppMs); assertEquals(2 * H, d.recordedAppMs)
    }

    @Test fun coverageStatuses() {
        val i = input(
            coverage = listOf(Interval(d1s + 12 * H, d1s + 24 * H), Interval(d1s + 48 * H, d1s + 72 * H)),
            notGranted = listOf(Interval(d1s + 30 * H, d1s + 31 * H)), now = d1s + 96 * H - 1,
        )
        val days = Analytics.days(i, d1.minusDays(1), d1.plusDays(3))
        assertEquals(CoverageStatus.UNKNOWN, days[0].coverage)       // before any data
        assertEquals(CoverageStatus.PARTIAL, days[1].coverage)       // half day
        assertEquals(CoverageStatus.NOT_GRANTED, days[2].coverage)   // access missing
        assertEquals(CoverageStatus.AVAILABLE, days[3].coverage)
        assertEquals(CoverageStatus.UNAVAILABLE, days[4].coverage)   // after coverage, no data
        assertNull("gaps are not zero", days[4].anyAppForegroundMs)
    }

    @Test fun futureDaysAreUnknownAndEmpty() {
        val i = input(now = d1s + 5 * H)
        val days = Analytics.days(i, d1, d2)
        // Coverage is relative to the elapsed part of today: fully covered so far = AVAILABLE.
        assertEquals(CoverageStatus.AVAILABLE, days[0].coverage)
        assertEquals(CoverageStatus.UNKNOWN, days[1].coverage)
        assertEquals(DaySource.NONE, days[1].source)
    }

    @Test fun excludedPackagesLeaveTotals() {
        val i = input(apps = listOf(app("a", d1s, d1s + H), app("launcher", d1s + H, d1s + 3 * H)), excluded = setOf("launcher"))
        val d = Analytics.days(i, d1, d1).single()
        assertEquals(H, d.anyAppForegroundMs); assertNull(d.perAppMs["launcher"])
    }

    @Test fun bucketAttributedByMidpoint() {
        val b = Bucket("a", d1s + 22 * H, d1s + 46 * H, 1) // midpoint d2 10:00
        assertEquals(d2, Analytics.bucketDay(b, zone))
    }
}
