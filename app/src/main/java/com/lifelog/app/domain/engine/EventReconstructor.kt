package com.lifelog.app.domain.engine

import com.lifelog.app.domain.AppSession
import com.lifelog.app.domain.Interval
import com.lifelog.app.domain.RawUsageEvent
import com.lifelog.app.domain.SystemEventType
import com.lifelog.app.domain.SystemMarker
import com.lifelog.app.domain.UsageEventCodes as C

/**
 * Reconstructs app foreground sessions and screen-interactive intervals from Android usage events.
 *
 * Rules (see docs/TRACKING_LIMITATIONS.md):
 * - Only intervals with an OBSERVED start and an OBSERVED end produce durations. Nothing is inferred.
 * - ACTIVITY_RESUMED opens an activity; ACTIVITY_PAUSED or ACTIVITY_STOPPED closes it.
 * - A duplicate RESUMED for an already-open activity keeps the earliest start.
 * - A close without an observed open is ignored (its start lies before the query window).
 * - DEVICE_SHUTDOWN / DEVICE_STARTUP discard every open interval: the Android reference states that open events
 *   across shutdown/startup "should be ignored because the closing time is unknown".
 * - Two SCREEN_INTERACTIVE without a NON_INTERACTIVE between them: the first has no observed end and is discarded.
 * - Per-package activity intervals are unioned, so multi-activity / multi-window apps are not double counted.
 * - Intervals still open at the end of the query are returned in [Result.openSince] so the next collection
 *   can re-query from that point; they are not stored yet.
 * - Events are processed in timestamp order (stable sort), which tolerates out-of-order delivery.
 */
object EventReconstructor {

    data class Result(
        val sessions: List<AppSession>,
        val screenOn: List<Interval>,
        val markers: List<SystemMarker>,
        /** Earliest start among intervals still open at the end of the input, or null. */
        val openSince: Long?,
        /** Number of open intervals discarded because their end could not be observed. */
        val discardedUnmatched: Int,
        val earliestEvent: Long?,
        val latestEvent: Long?,
    )

    fun reconstruct(events: List<RawUsageEvent>): Result {
        val sorted = events.sortedBy { it.timestampUtc } // stable
        val openActivities = HashMap<Pair<String, String>, Long>()
        val perPackage = HashMap<String, MutableList<Interval>>()
        val screen = ArrayList<Interval>()
        val markers = ArrayList<SystemMarker>()
        var screenOpenAt: Long? = null
        var discarded = 0

        for (e in sorted) {
            when (e.type) {
                C.ACTIVITY_RESUMED -> {
                    val pkg = e.packageName ?: continue
                    val key = pkg to (e.className ?: "")
                    openActivities.putIfAbsent(key, e.timestampUtc)
                }

                C.ACTIVITY_PAUSED, C.ACTIVITY_STOPPED -> {
                    val pkg = e.packageName ?: continue
                    val key = pkg to (e.className ?: "")
                    val start = openActivities.remove(key) ?: continue
                    if (e.timestampUtc > start) {
                        perPackage.getOrPut(pkg) { ArrayList() } += Interval(start, e.timestampUtc)
                    }
                }

                C.SCREEN_INTERACTIVE -> {
                    if (screenOpenAt != null) discarded++
                    screenOpenAt = e.timestampUtc
                    markers += SystemMarker(SystemEventType.SCREEN_INTERACTIVE, e.timestampUtc, e.type)
                }

                C.SCREEN_NON_INTERACTIVE -> {
                    val start = screenOpenAt
                    if (start != null && e.timestampUtc > start) screen += Interval(start, e.timestampUtc)
                    screenOpenAt = null
                    markers += SystemMarker(SystemEventType.SCREEN_NON_INTERACTIVE, e.timestampUtc, e.type)
                }

                C.KEYGUARD_SHOWN -> markers += SystemMarker(SystemEventType.KEYGUARD_SHOWN, e.timestampUtc, e.type)
                C.KEYGUARD_HIDDEN -> markers += SystemMarker(SystemEventType.KEYGUARD_HIDDEN, e.timestampUtc, e.type)

                C.DEVICE_SHUTDOWN, C.DEVICE_STARTUP -> {
                    discarded += openActivities.size + (if (screenOpenAt != null) 1 else 0)
                    openActivities.clear()
                    screenOpenAt = null
                    val t = if (e.type == C.DEVICE_SHUTDOWN) SystemEventType.DEVICE_SHUTDOWN else SystemEventType.DEVICE_STARTUP
                    markers += SystemMarker(t, e.timestampUtc, e.type)
                }

                else -> Unit
            }
        }

        val sessions = perPackage.flatMap { (pkg, ivs) -> IntervalMath.union(ivs).map { AppSession(pkg, it) } }
            .sortedBy { it.interval.start }
        val openCandidates = openActivities.values + listOfNotNull(screenOpenAt)
        return Result(
            sessions = sessions,
            screenOn = IntervalMath.union(screen),
            markers = markers.distinctBy { it.type to it.timestampUtc },
            openSince = openCandidates.minOrNull(),
            discardedUnmatched = discarded,
            earliestEvent = sorted.firstOrNull()?.timestampUtc,
            latestEvent = sorted.lastOrNull()?.timestampUtc,
        )
    }

    /**
     * Counts foreground entries: a session counts as a new entry when it starts more than [gapMs] after the previous
     * session of the same package ended. Short gaps (activity-to-activity transitions) are not new entries.
     */
    fun countEntries(sessions: List<Interval>, gapMs: Long = 2_000): Int {
        var count = 0
        var lastEnd = Long.MIN_VALUE
        for (iv in IntervalMath.union(sessions)) {
            if (lastEnd == Long.MIN_VALUE || iv.start - lastEnd > gapMs) count++
            lastEnd = iv.end
        }
        return count
    }
}
