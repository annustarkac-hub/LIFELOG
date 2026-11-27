package com.lifelog.app.data

import androidx.room.withTransaction
import com.lifelog.app.data.db.AppIdentityEntity
import com.lifelog.app.data.db.BatterySnapshotEntity
import com.lifelog.app.data.db.CollectionCheckpointEntity
import com.lifelog.app.data.db.DataCoverageEntity
import com.lifelog.app.data.db.DeviceProfileEntity
import com.lifelog.app.data.db.LifelogDatabase
import com.lifelog.app.data.db.SystemEventEntity
import com.lifelog.app.data.db.UsageRecordEntity
import com.lifelog.app.domain.CoverageKind
import com.lifelog.app.domain.CoverageStatus

/** Single write boundary over Room. All multi-table writes happen inside [transaction]. */
class LifelogRepository(val db: LifelogDatabase) {
    val usage = db.usageDao()
    val events = db.systemEventDao()
    val battery = db.batteryDao()
    val coverage = db.coverageDao()
    val meta = db.metaDao()

    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    suspend fun checkpoint(): CollectionCheckpointEntity? = meta.checkpoint(EVENTS_COLLECTOR)
    suspend fun saveCheckpoint(cp: CollectionCheckpointEntity) = meta.upsertCheckpoint(cp)

    suspend fun upsertRecords(records: List<UsageRecordEntity>) {
        records.chunked(500).forEach { usage.upsertRecords(it) }
    }

    suspend fun upsertSystemEvents(list: List<SystemEventEntity>) {
        list.chunked(500).forEach { events.upsert(it) }
    }

    suspend fun addBatterySnapshot(s: BatterySnapshotEntity) {
        battery.insert(listOf(s))
    }

    /**
     * Adds a coverage interval, extending the latest row of the same kind when contiguous/overlapping so the
     * table stays small. Coverage is only ever added, never shrunk, during collection.
     */
    suspend fun addCoverage(kind: CoverageKind, start: Long, end: Long, now: Long, source: String, notes: String? = null) {
        if (end <= start) return
        val latest = coverage.latest(kind.name)
        if (latest != null && start <= latest.intervalEndUtc && start >= latest.intervalStartUtc) {
            if (end > latest.intervalEndUtc) coverage.extend(latest.id, end, now)
            return
        }
        val status = if (kind == CoverageKind.EVENTS) CoverageStatus.AVAILABLE else CoverageStatus.NOT_GRANTED
        coverage.insert(DataCoverageEntity(0, kind.name, start, end, status.name, source, now, notes))
    }

    suspend fun profile(): DeviceProfileEntity? = meta.profile()
    suspend fun saveProfile(p: DeviceProfileEntity) = meta.upsertProfile(p)

    suspend fun upsertApps(apps: List<AppIdentityEntity>) = meta.upsertApps(apps)

    suspend fun recordCounts(): Map<String, Int> = mapOf(
        "usage_record" to usage.count(),
        "system_event" to events.count(),
        "battery_snapshot" to battery.count(),
        "data_coverage" to coverage.count(),
        "app_identity" to meta.allApps().size,
    )

    /** Removes every app-owned row in one transaction. */
    suspend fun deleteAll() = transaction {
        usage.deleteAll()
        events.deleteAll()
        battery.deleteAll()
        coverage.deleteAll()
        meta.deleteApps()
        meta.deleteCheckpoints()
        meta.deleteProfile()
        meta.deleteBackupMetadata()
    }

    companion object {
        const val EVENTS_COLLECTOR = "usage_events"
    }
}
