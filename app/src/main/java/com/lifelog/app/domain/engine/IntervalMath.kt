package com.lifelog.app.domain.engine

import com.lifelog.app.domain.Interval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Pure interval helpers. All intervals are half-open [start, end). */
object IntervalMath {

    /** Merges overlapping or touching intervals. The result is sorted and non-overlapping. */
    fun union(intervals: Iterable<Interval>): List<Interval> {
        val sorted = intervals.filter { it.length > 0 }.sortedBy { it.start }
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<Interval>(sorted.size)
        var curStart = sorted[0].start
        var curEnd = sorted[0].end
        for (i in 1 until sorted.size) {
            val iv = sorted[i]
            if (iv.start <= curEnd) {
                if (iv.end > curEnd) curEnd = iv.end
            } else {
                out += Interval(curStart, curEnd)
                curStart = iv.start
                curEnd = iv.end
            }
        }
        out += Interval(curStart, curEnd)
        return out
    }

    fun totalLength(intervals: Iterable<Interval>): Long = union(intervals).sumOf { it.length }

    /** Clips each interval to [bounds]; drops those outside. */
    fun clip(intervals: Iterable<Interval>, bounds: Interval): List<Interval> =
        intervals.mapNotNull { it.intersect(bounds) }

    /** UTC interval of a local calendar day. Correct across DST transitions (23h/25h days). */
    fun dayBounds(date: LocalDate, zone: ZoneId): Interval {
        val s = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val e = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return Interval(s, e)
    }

    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /**
     * Splits intervals at local midnights and returns the total duration per local date.
     * Input is unioned first, so overlapping input never double counts.
     */
    fun splitByLocalDay(intervals: Iterable<Interval>, zone: ZoneId): Map<LocalDate, Long> {
        val result = sortedMapOf<LocalDate, Long>()
        for (iv in union(intervals)) {
            var date = localDate(iv.start, zone)
            var cursor = iv.start
            while (cursor < iv.end) {
                val day = dayBounds(date, zone)
                val segEnd = minOf(iv.end, day.end)
                if (segEnd > cursor) result[date] = (result[date] ?: 0L) + (segEnd - cursor)
                cursor = segEnd
                date = date.plusDays(1)
            }
        }
        return result
    }

    /** Length of [bounds] covered by the union of [intervals]. */
    fun coveredLength(intervals: Iterable<Interval>, bounds: Interval): Long =
        totalLength(clip(intervals, bounds))
}
