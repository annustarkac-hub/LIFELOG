package com.lifelog.app.platform

import com.lifelog.app.domain.RawUsageEvent

/** Android aggregated per-app bucket (UsageStats). */
data class AppBucket(
    val packageName: String,
    val bucketStart: Long,
    val bucketEnd: Long,
    val totalForegroundMs: Long,
    val lastTimeUsed: Long,
)

/** Android aggregated SCREEN_INTERACTIVE bucket (EventStats). */
data class ScreenBucket(val bucketStart: Long, val bucketEnd: Long, val totalTimeMs: Long, val count: Int)

enum class BucketInterval(val androidConstant: Int) { DAILY(0), WEEKLY(1), MONTHLY(2), YEARLY(3) }

/** Boundary around UsageStatsManager. Every method returns null when Android returns nothing usable. */
interface UsageStatsSource {
    fun hasUsageAccess(): Boolean

    /** Null if Usage Access is missing or Android returned null (e.g. user locked, per API reference). */
    fun queryEvents(begin: Long, end: Long): List<RawUsageEvent>?

    fun queryAppBuckets(interval: BucketInterval, begin: Long, end: Long): List<AppBucket>?

    fun queryScreenBuckets(interval: BucketInterval, begin: Long, end: Long): List<ScreenBucket>?
}

data class AppInfo(val packageName: String, val label: String?, val installed: Boolean)

interface AppInfoSource {
    fun info(packageName: String): AppInfo
}

data class BatteryReading(val levelPercent: Int?, val chargingState: String?, val pluggedType: String?)

interface BatterySource {
    fun read(): BatteryReading?
}

data class DeviceInfo(val manufacturer: String, val model: String, val androidRelease: String, val apiLevel: Int)

interface DeviceInfoSource {
    fun read(): DeviceInfo
    fun isInteractive(): Boolean
}

fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
