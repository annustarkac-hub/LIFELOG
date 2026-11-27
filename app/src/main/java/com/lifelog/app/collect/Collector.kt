package com.lifelog.app.collect

import com.lifelog.app.data.LifelogRepository
import com.lifelog.app.data.db.AppIdentityEntity
import com.lifelog.app.data.db.BatterySnapshotEntity
import com.lifelog.app.data.db.CollectionCheckpointEntity
import com.lifelog.app.data.db.DeviceProfileEntity
import com.lifelog.app.data.db.SystemEventEntity
import com.lifelog.app.data.db.UsageRecordEntity
import com.lifelog.app.domain.CoverageKind
import com.lifelog.app.domain.MetricType
import com.lifelog.app.domain.SourceType
import com.lifelog.app.domain.engine.EventReconstructor
import com.lifelog.app.platform.AppInfoSource
import com.lifelog.app.platform.BatterySource
import com.lifelog.app.platform.BucketInterval
import com.lifelog.app.platform.Clock
import com.lifelog.app.platform.DeviceInfoSource
import com.lifelog.app.platform.UsageStatsSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface CollectionOutcome {
    data class Success(val sessions: Int, val screenIntervals: Int, val markers: Int, val discardedUnmatched: Int) : CollectionOutcome
    data object TrackingDisabled : CollectionOutcome
    data object NotGranted : CollectionOutcome
    data object AndroidReturnedNoData : CollectionOutcome
}

/**
 * Collection pipeline (SYSTEM_ARCHITECTURE.md "Collection model"):
 * check access → bounded query → normalise → persist with provenance → idempotent reconcile → coverage/checkpoint.
 *
 * It runs on app open and from an inexact periodic WorkManager job. It never assumes it ran on time: every run
 * re-queries from the last checkpoint (minus an overlap, or from the earliest still-open interval).
 */
class Collector(
    private val repo: LifelogRepository,
    private val usage: UsageStatsSource,
    private val appInfo: AppInfoSource,
    private val battery: BatterySource,
    private val device: DeviceInfoSource,
    private val clock: Clock,
    private val trackingEnabled: suspend () -> Boolean,
) {
    private val mutex = Mutex()

    suspend fun collect(): CollectionOutcome = mutex.withLock { collectLocked() }

    private suspend fun collectLocked(): CollectionOutcome {
        val now = clock.now()
        if (!trackingEnabled()) return CollectionOutcome.TrackingDisabled
        val cp = repo.checkpoint()

        if (!usage.hasUsageAccess()) {
            // Pause, preserve data, record the gap as NOT_GRANTED. Nothing is deleted.
            repo.transaction {
                val from = cp?.lastSuccessfulQueryEndUtc ?: cp?.lastAttemptAtUtc ?: now
                repo.addCoverage(CoverageKind.NOT_GRANTED, from, now, now, SOURCE_APPOPS)
                repo.saveCheckpoint(baseCheckpoint(cp, now).copy(status = STATUS_NOT_GRANTED, errorCode = "USAGE_ACCESS_NOT_GRANTED"))
            }
            return CollectionOutcome.NotGranted
        }

        val lastEnd = cp?.lastSuccessfulQueryEndUtc
        val begin = maxOf(
            now - MAX_LOOKBACK_MS,
            if (lastEnd == null) now - MAX_LOOKBACK_MS else minOf(lastEnd - OVERLAP_MS, cp?.openSinceUtc ?: Long.MAX_VALUE),
        )
        val events = usage.queryEvents(begin, now)
        if (events == null) {
            repo.saveCheckpoint(baseCheckpoint(cp, now).copy(status = STATUS_NO_DATA, errorCode = "QUERY_EVENTS_NULL"))
            return CollectionOutcome.AndroidReturnedNoData
        }
        val r = EventReconstructor.reconstruct(events)

        // Coverage: claim the query window only when it continues a recent checkpoint. Otherwise (first run, or a
        // long gap that may exceed Android's "few days" event retention) coverage starts at the earliest event
        // Android actually returned: we cannot tell "no usage" from "not retained" before that point.
        val coverageStart = if (lastEnd != null && now - begin <= CONTIGUOUS_CLAIM_MS) begin else (r.earliestEvent ?: now)

        repo.transaction {
            val existing = repo.profile()
            val info = device.read()
            val firstTracking = existing?.firstLifelogTrackingAtUtc ?: now
            repo.saveProfile(
                DeviceProfileEntity(
                    manufacturer = info.manufacturer, model = info.model, androidRelease = info.androidRelease,
                    apiLevel = info.apiLevel, firstLifelogTrackingAtUtc = firstTracking,
                    userPurchaseDate = existing?.userPurchaseDate, purchaseDateSource = existing?.purchaseDateSource,
                    lastSuccessfulCollectionAtUtc = now,
                )
            )
            repo.upsertRecords(
                r.sessions.map {
                    eventRecord(MetricType.APP_FOREGROUND, it.packageName, it.interval.start, it.interval.end, begin, now, firstTracking)
                } + r.screenOn.map {
                    eventRecord(MetricType.DEVICE_SCREEN_ON, null, it.start, it.end, begin, now, firstTracking)
                }
            )
            repo.upsertSystemEvents(
                r.markers.map {
                    SystemEventEntity(
                        eventType = it.type.name, eventTimeUtc = it.timestampUtc, packageName = null, valueText = null,
                        valueNumber = null, sourceApi = API_QUERY_EVENTS, sourceEventType = it.sourceEventType,
                        dedupeKey = "SE|${it.type.name}|${it.timestampUtc}", collectedAtUtc = now,
                    )
                }
            )
            repo.addCoverage(CoverageKind.EVENTS, coverageStart, now, now, API_QUERY_EVENTS)
            val pkgs = r.sessions.map { it.packageName }.distinct()
            if (pkgs.isNotEmpty()) {
                val known = repo.meta.apps(pkgs).associateBy { it.packageName }
                repo.upsertApps(pkgs.map { pkg ->
                    val k = known[pkg]
                    val i = appInfo.info(pkg)
                    val lastSeen = r.sessions.filter { it.packageName == pkg }.maxOf { it.interval.end }
                    AppIdentityEntity(
                        packageName = pkg, displayName = i.label ?: k?.displayName,
                        firstSeenAt = k?.firstSeenAt ?: r.sessions.first { it.packageName == pkg }.interval.start,
                        lastSeenAt = maxOf(lastSeen, k?.lastSeenAt ?: 0), isCurrentlyInstalled = i.installed,
                    )
                })
            }
            repo.saveCheckpoint(
                baseCheckpoint(cp, now).copy(
                    lastSuccessfulQueryEndUtc = now, lastSuccessfulRunAtUtc = now, openSinceUtc = r.openSince,
                    status = STATUS_OK, errorCode = null,
                )
            )
        }

        val lastRefresh = cp?.lastSummaryRefreshUtc
        if (lastRefresh == null || now - lastRefresh >= SUMMARY_REFRESH_MS) {
            importAndroidAggregates(now)
        }
        battery.read()?.let {
            repo.addBatterySnapshot(BatterySnapshotEntity(0, now, it.levelPercent, it.chargingState, it.pluggedType, API_BATTERY))
        }
        return CollectionOutcome.Success(r.sessions.size, r.screenOn.size, r.markers.size, r.discardedUnmatched)
    }

    /**
     * Imports Android aggregate buckets. Daily buckets are kept only when they END at or before the earliest
     * event coverage, so they describe days LIFELOG has no session detail for. Analytics additionally ignores
     * buckets on any day that has event coverage. Monthly/yearly buckets are stored as separate "Android summaries".
     */
    suspend fun importAndroidAggregates(now: Long) {
        val earliestEventCoverage = repo.coverage.earliestStart(CoverageKind.EVENTS.name) ?: now
        val historyStart = now - AGGREGATE_LOOKBACK_MS
        val records = ArrayList<UsageRecordEntity>()

        usage.queryAppBuckets(BucketInterval.DAILY, historyStart, earliestEventCoverage)
            ?.filter { it.bucketEnd <= earliestEventCoverage && it.bucketEnd > it.bucketStart }
            ?.forEach { records += aggRecord(MetricType.API_APP_DAILY, it.packageName, it.bucketStart, it.bucketEnd, it.totalForegroundMs, historyStart, earliestEventCoverage, now, API_QUERY_USAGE_STATS) }
        usage.queryScreenBuckets(BucketInterval.DAILY, historyStart, earliestEventCoverage)
            ?.filter { it.bucketEnd <= earliestEventCoverage && it.bucketEnd > it.bucketStart }
            ?.forEach { records += aggRecord(MetricType.API_SCREEN_DAILY, null, it.bucketStart, it.bucketEnd, it.totalTimeMs, historyStart, earliestEventCoverage, now, API_QUERY_EVENT_STATS) }

        for ((interval, appMetric, screenMetric) in listOf(
            Triple(BucketInterval.MONTHLY, MetricType.API_APP_MONTHLY, MetricType.API_SCREEN_MONTHLY),
            Triple(BucketInterval.YEARLY, MetricType.API_APP_YEARLY, MetricType.API_SCREEN_YEARLY),
        )) {
            usage.queryAppBuckets(interval, historyStart, now)
                ?.filter { it.bucketEnd > it.bucketStart }
                ?.forEach { records += aggRecord(appMetric, it.packageName, it.bucketStart, it.bucketEnd, it.totalForegroundMs, historyStart, now, now, API_QUERY_USAGE_STATS) }
            usage.queryScreenBuckets(interval, historyStart, now)
                ?.filter { it.bucketEnd > it.bucketStart }
                ?.forEach { records += aggRecord(screenMetric, null, it.bucketStart, it.bucketEnd, it.totalTimeMs, historyStart, now, now, API_QUERY_EVENT_STATS) }
        }

        repo.transaction {
            repo.upsertRecords(records)
            val pkgs = records.mapNotNull { it.packageName }.distinct()
            val known = repo.meta.apps(pkgs).associateBy { it.packageName }
            repo.upsertApps(pkgs.filter { it !in known }.map { pkg ->
                val i = appInfo.info(pkg)
                AppIdentityEntity(pkg, i.label, null, null, i.installed)
            })
            repo.checkpoint()?.let { repo.saveCheckpoint(it.copy(lastSummaryRefreshUtc = now)) }
        }
    }

    private fun baseCheckpoint(cp: CollectionCheckpointEntity?, now: Long) = (cp ?: CollectionCheckpointEntity(
        collectorName = LifelogRepository.EVENTS_COLLECTOR, lastSuccessfulQueryEndUtc = null, lastSuccessfulRunAtUtc = null,
        openSinceUtc = null, lastAttemptAtUtc = null, lastSummaryRefreshUtc = null, status = STATUS_OK, errorCode = null,
    )).copy(lastAttemptAtUtc = now)

    private fun eventRecord(metric: MetricType, pkg: String?, start: Long, end: Long, qStart: Long, qEnd: Long, firstTracking: Long) =
        UsageRecordEntity(
            packageName = pkg, metricType = metric.name, startAtUtc = start, endAtUtc = end, durationMs = end - start,
            sourceType = SourceType.USAGE_EVENTS.name, sourceApi = API_QUERY_EVENTS, sourceQueryStartUtc = qStart,
            sourceQueryEndUtc = qEnd, collectedAtUtc = qEnd, isHistorical = start < firstTracking, isInferred = false,
            dedupeKey = "${metric.name}|${pkg ?: "-"}|$start|$end",
        )

    private fun aggRecord(metric: MetricType, pkg: String?, start: Long, end: Long, total: Long, qStart: Long, qEnd: Long, now: Long, api: String) =
        UsageRecordEntity(
            packageName = pkg, metricType = metric.name, startAtUtc = start, endAtUtc = end, durationMs = total,
            sourceType = SourceType.ANDROID_AGGREGATE.name, sourceApi = api, sourceQueryStartUtc = qStart,
            sourceQueryEndUtc = qEnd, collectedAtUtc = now, isHistorical = true, isInferred = false,
            dedupeKey = "${metric.name}|${pkg ?: "-"}|$start",
        )

    companion object {
        const val API_QUERY_EVENTS = "UsageStatsManager.queryEvents"
        const val API_QUERY_USAGE_STATS = "UsageStatsManager.queryUsageStats"
        const val API_QUERY_EVENT_STATS = "UsageStatsManager.queryEventStats"
        const val API_BATTERY = "ACTION_BATTERY_CHANGED(sticky)"
        const val SOURCE_APPOPS = "AppOpsManager.OPSTR_GET_USAGE_STATS"
        const val STATUS_OK = "OK"
        const val STATUS_NOT_GRANTED = "NOT_GRANTED"
        const val STATUS_NO_DATA = "ANDROID_RETURNED_NULL"

        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        /** Android documents event retention only as "a few days"; we ask for up to 30 days and keep what returns. */
        const val MAX_LOOKBACK_MS = 30 * DAY
        const val OVERLAP_MS = 15 * 60_000L
        const val CONTIGUOUS_CLAIM_MS = DAY
        const val SUMMARY_REFRESH_MS = 6 * HOUR
        const val AGGREGATE_LOOKBACK_MS = 5 * 366 * DAY
    }
}
