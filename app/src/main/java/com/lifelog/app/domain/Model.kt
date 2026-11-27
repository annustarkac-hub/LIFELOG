package com.lifelog.app.domain

/** Metric families. Device screen-on and app foreground are deliberately distinct types. */
enum class MetricType {
    /** One app's foreground interval reconstructed from ACTIVITY_RESUMED/PAUSED/STOPPED events. */
    APP_FOREGROUND,

    /** Display interactive interval reconstructed from SCREEN_INTERACTIVE/NON_INTERACTIVE events. */
    DEVICE_SCREEN_ON,

    /** Android aggregated per-app foreground total for a daily bucket (UsageStatsManager.queryUsageStats). */
    API_APP_DAILY,

    /** Android aggregated SCREEN_INTERACTIVE total for a daily bucket (UsageStatsManager.queryEventStats). */
    API_SCREEN_DAILY,

    /** Coarse Android-retained summaries; shown separately, never merged into day totals. */
    API_APP_MONTHLY,
    API_APP_YEARLY,
    API_SCREEN_MONTHLY,
    API_SCREEN_YEARLY,
}

/** Where a value came from. */
enum class SourceType {
    /** Reconstructed by LIFELOG from Android usage events. */
    USAGE_EVENTS,

    /** Aggregated bucket returned by Android. */
    ANDROID_AGGREGATE,
}

enum class CoverageStatus { AVAILABLE, PARTIAL, UNAVAILABLE, NOT_GRANTED, UNKNOWN }

/** Coverage interval kinds stored in data_coverage. */
enum class CoverageKind {
    /** Usage events were successfully read for this interval. */
    EVENTS,

    /** Collection attempted while Usage Access was not granted. */
    NOT_GRANTED,
}

enum class SystemEventType {
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    KEYGUARD_SHOWN,
    KEYGUARD_HIDDEN,
    DEVICE_STARTUP,
    DEVICE_SHUTDOWN,
}

/** A raw Android usage event, platform-independent so the engine is unit-testable. */
data class RawUsageEvent(
    val timestampUtc: Long,
    val type: Int,
    val packageName: String?,
    val className: String?,
)

/** Constants mirroring android.app.usage.UsageEvents.Event (values from the public API reference). */
object UsageEventCodes {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val ACTIVITY_STOPPED = 23
    const val DEVICE_SHUTDOWN = 26
    const val DEVICE_STARTUP = 27

    fun name(code: Int): String = when (code) {
        ACTIVITY_RESUMED -> "ACTIVITY_RESUMED"
        ACTIVITY_PAUSED -> "ACTIVITY_PAUSED"
        3 -> "END_OF_DAY"
        4 -> "CONTINUE_PREVIOUS_DAY"
        5 -> "CONFIGURATION_CHANGE"
        7 -> "USER_INTERACTION"
        8 -> "SHORTCUT_INVOCATION"
        10 -> "NOTIFICATION_SEEN"
        11 -> "STANDBY_BUCKET_CHANGED"
        12 -> "NOTIFICATION_INTERRUPTION"
        SCREEN_INTERACTIVE -> "SCREEN_INTERACTIVE"
        SCREEN_NON_INTERACTIVE -> "SCREEN_NON_INTERACTIVE"
        KEYGUARD_SHOWN -> "KEYGUARD_SHOWN"
        KEYGUARD_HIDDEN -> "KEYGUARD_HIDDEN"
        19 -> "FOREGROUND_SERVICE_START"
        20 -> "FOREGROUND_SERVICE_STOP"
        ACTIVITY_STOPPED -> "ACTIVITY_STOPPED"
        DEVICE_SHUTDOWN -> "DEVICE_SHUTDOWN"
        DEVICE_STARTUP -> "DEVICE_STARTUP"
        else -> "TYPE_$code"
    }
}

/** Half-open interval [start, end) in UTC epoch millis. */
data class Interval(val start: Long, val end: Long) {
    init {
        require(end >= start) { "Interval end $end before start $start" }
    }

    val length: Long get() = end - start
    fun overlaps(other: Interval) = start < other.end && other.start < end
    fun intersect(other: Interval): Interval? {
        val s = maxOf(start, other.start)
        val e = minOf(end, other.end)
        return if (e > s) Interval(s, e) else null
    }
}

/** A reconstructed foreground interval for one package. */
data class AppSession(val packageName: String, val interval: Interval)

/** A system marker event observed in the usage event stream. */
data class SystemMarker(val type: SystemEventType, val timestampUtc: Long, val sourceEventType: Int)
