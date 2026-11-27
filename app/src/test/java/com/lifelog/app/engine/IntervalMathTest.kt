package com.lifelog.app.engine

import com.lifelog.app.domain.Interval
import com.lifelog.app.domain.engine.IntervalMath
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class IntervalMathTest {
    private val H = 3_600_000L

    @Test fun unionMergesOverlappingAndTouching() {
        val u = IntervalMath.union(listOf(Interval(0, 10), Interval(5, 20), Interval(20, 30), Interval(40, 50), Interval(45, 45)))
        assertEquals(listOf(Interval(0, 30), Interval(40, 50)), u)
        assertEquals(40, IntervalMath.totalLength(listOf(Interval(0, 10), Interval(5, 20), Interval(20, 30), Interval(40, 50))))
    }

    @Test fun duplicateIntervalsCountOnce() {
        assertEquals(10, IntervalMath.totalLength(List(5) { Interval(100, 110) }))
    }

    @Test fun splitAcrossMidnight() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = ZonedDateTime.of(2026, 11, 26, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val m = IntervalMath.splitByLocalDay(listOf(Interval(start, start + H)), zone)
        assertEquals(30 * 60_000L, m[LocalDate.of(2026, 11, 26)])
        assertEquals(30 * 60_000L, m[LocalDate.of(2026, 11, 27)])
    }

    @Test fun dstSpringForwardDayIs23Hours() {
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals(23 * H, IntervalMath.dayBounds(LocalDate.of(2026, 3, 29), zone).length)
        val day = IntervalMath.dayBounds(LocalDate.of(2026, 3, 29), zone)
        assertEquals(mapOf(LocalDate.of(2026, 3, 29) to 23 * H), IntervalMath.splitByLocalDay(listOf(day), zone))
    }

    @Test fun dstFallBackDayIs25Hours() {
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals(25 * H, IntervalMath.dayBounds(LocalDate.of(2026, 10, 25), zone).length)
    }

    @Test fun sameInstantsAttributeToDifferentDaysInDifferentZones() {
        // 2026-11-26T20:00Z is the 26th in New York and the 27th in Tokyo: buckets are recomputed per current zone.
        val t = ZonedDateTime.of(2026, 11, 26, 20, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 26), IntervalMath.localDate(t, ZoneId.of("America/New_York")))
        assertEquals(LocalDate.of(2026, 11, 27), IntervalMath.localDate(t, ZoneId.of("Asia/Tokyo")))
    }

    @Test fun coveredLengthClipsToBounds() {
        assertEquals(5, IntervalMath.coveredLength(listOf(Interval(0, 10), Interval(8, 12)), Interval(7, 12)))
    }
}
