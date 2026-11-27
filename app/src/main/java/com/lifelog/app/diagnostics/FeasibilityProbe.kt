package com.lifelog.app.diagnostics

import com.lifelog.app.domain.UsageEventCodes
import com.lifelog.app.platform.AndroidAppInfoSource
import com.lifelog.app.platform.BatterySource
import com.lifelog.app.platform.BucketInterval
import com.lifelog.app.platform.Clock
import com.lifelog.app.platform.DeviceInfoSource
import com.lifelog.app.platform.UsageStatsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Gate 2 feasibility demonstrator, shipped inside the app (Settings > Diagnostics) so the owner can measure what
 * THEIR device actually returns. It reports counts and date ranges only — no per-app usage — so the text can be
 * shared for review without exposing which apps were used.
 */
class FeasibilityProbe(
    private val usage: UsageStatsSource,
    private val apps: AndroidAppInfoSource,
    private val device: DeviceInfoSource,
    private val battery: BatterySource,
    private val clock: Clock,
    private val appVersion: String,
) {
    suspend fun run(): String = withContext(Dispatchers.IO) {
        val now = clock.now()
        val sb = StringBuilder()
        fun line(s: String) = sb.append(s).append('\n')
        fun ts(t: Long?) = t?.let { Instant.ofEpochMilli(it).toString() } ?: "none"
        val d = device.read()
        line("LIFELOG feasibility probe v1 (app $appVersion)")
        line("Run at (UTC): ${ts(now)}")
        line("Device: ${d.manufacturer} ${d.model}, Android ${d.androidRelease} (API ${d.apiLevel})")
        val granted = usage.hasUsageAccess()
        line("Usage Access granted: $granted")
        line("Screen interactive now: ${device.isInteractive()}")
        if (!granted) {
            line("Grant Usage Access and run again to measure data availability.")
            return@withContext sb.toString()
        }
        line("")
        line("== queryEvents, requested window: last 30 days ==")
        val begin = now - 30L * 86_400_000
        val events = usage.queryEvents(begin, now)
        if (events == null) {
            line("Android returned null (device locked or access missing).")
        } else {
            line("Relevant events returned: ${events.size}")
            line("Earliest event: ${ts(events.minOfOrNull { it.timestampUtc })}")
            line("Latest event:   ${ts(events.maxOfOrNull { it.timestampUtc })}")
            val days = events.minOfOrNull { it.timestampUtc }?.let { (now - it) / 3_600_000.0 / 24 }
            line("Observed event retention: ${days?.let { "%.1f days".format(it) } ?: "n/a"}")
            events.groupingBy { it.type }.eachCount().toSortedMap().forEach { (k, v) -> line("  ${UsageEventCodes.name(k)} ($k): $v") }
            line("Distinct packages in events: ${events.mapNotNull { it.packageName }.distinct().size}")
        }
        line("")
        line("== queryUsageStats / queryEventStats, window: last 5 years ==")
        val longBegin = now - 5L * 366 * 86_400_000
        for (interval in BucketInterval.entries) {
            val b = usage.queryAppBuckets(interval, longBegin, now)
            val s = usage.queryScreenBuckets(interval, longBegin, now)
            line("${interval.name}: app buckets=${b?.size ?: "null"}, distinct bucket starts=${b?.map { it.bucketStart }?.distinct()?.size ?: 0}, " +
                "earliest=${ts(b?.minOfOrNull { it.bucketStart })}; screen buckets=${s?.size ?: "null"}, earliest=${ts(s?.minOfOrNull { it.bucketStart })}")
        }
        line("")
        line("== Package visibility ==")
        line("Launcher-visible packages: ${apps.launcherVisibleCount()}")
        val pkgs = events?.mapNotNull { it.packageName }?.distinct().orEmpty()
        val labelled = pkgs.count { apps.info(it).label != null }
        line("Packages seen in events with resolvable label: $labelled / ${pkgs.size}")
        line("")
        line("== Battery (sticky ACTION_BATTERY_CHANGED sample) ==")
        val br = battery.read()
        line("Level: ${br?.levelPercent ?: "n/a"}%, status: ${br?.chargingState ?: "n/a"}, plugged: ${br?.pluggedType ?: "n/a"}")
        line("")
        line("Interpretation: history older than the earliest event/bucket above is not available from Android on this device.")
        sb.toString()
    }
}
