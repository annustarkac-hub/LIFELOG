package com.lifelog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    /** REPLACE on dedupe_key: re-collecting the same window yields identical rows (idempotent). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecords(records: List<UsageRecordEntity>)

    /** IGNORE on dedupe_key: used by backup merge so existing rows win. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecordsIgnore(records: List<UsageRecordEntity>): List<Long>

    @Query(
        """SELECT package_name, start_at_utc, end_at_utc, duration_ms, is_historical FROM usage_record
           WHERE metric_type = :metric AND start_at_utc < :to AND end_at_utc > :from"""
    )
    suspend fun intervals(metric: String, from: Long, to: Long): List<IntervalRow>

    @Query(
        """SELECT package_name, start_at_utc, end_at_utc, duration_ms, is_historical FROM usage_record
           WHERE metric_type = :metric AND package_name = :pkg AND start_at_utc < :to AND end_at_utc > :from"""
    )
    suspend fun intervalsForPackage(metric: String, pkg: String, from: Long, to: Long): List<IntervalRow>

    @Query("SELECT * FROM usage_record WHERE metric_type = :metric ORDER BY start_at_utc")
    suspend fun allOfMetric(metric: String): List<UsageRecordEntity>

    @Query("SELECT * FROM usage_record ORDER BY id")
    suspend fun all(): List<UsageRecordEntity>

    @Query("SELECT MIN(start_at_utc) FROM usage_record WHERE metric_type IN (:metrics)")
    suspend fun earliestStart(metrics: List<String>): Long?

    @Query("SELECT COUNT(*) FROM usage_record")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM usage_record")
    fun countFlow(): Flow<Int>

    @Query("SELECT DISTINCT package_name FROM usage_record WHERE package_name IS NOT NULL")
    suspend fun distinctPackages(): List<String>

    @Query("DELETE FROM usage_record")
    suspend fun deleteAll()
}

@Dao
interface SystemEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(events: List<SystemEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(events: List<SystemEventEntity>): List<Long>

    @Query("SELECT * FROM system_event WHERE event_time_utc >= :from AND event_time_utc < :to ORDER BY event_time_utc")
    suspend fun inRange(from: Long, to: Long): List<SystemEventEntity>

    @Query("SELECT * FROM system_event ORDER BY id")
    suspend fun all(): List<SystemEventEntity>

    @Query("SELECT COUNT(*) FROM system_event")
    suspend fun count(): Int

    @Query("DELETE FROM system_event")
    suspend fun deleteAll()
}

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snapshots: List<BatterySnapshotEntity>): List<Long>

    @Query("SELECT * FROM battery_snapshot WHERE captured_at_utc >= :from AND captured_at_utc < :to ORDER BY captured_at_utc")
    suspend fun inRange(from: Long, to: Long): List<BatterySnapshotEntity>

    @Query("SELECT * FROM battery_snapshot ORDER BY captured_at_utc DESC LIMIT 1")
    suspend fun latest(): BatterySnapshotEntity?

    @Query("SELECT * FROM battery_snapshot ORDER BY id")
    suspend fun all(): List<BatterySnapshotEntity>

    @Query("SELECT COUNT(*) FROM battery_snapshot")
    suspend fun count(): Int

    @Query("DELETE FROM battery_snapshot")
    suspend fun deleteAll()
}

@Dao
interface CoverageDao {
    @Insert
    suspend fun insert(row: DataCoverageEntity): Long

    @Insert
    suspend fun insertAll(rows: List<DataCoverageEntity>)

    @Query("UPDATE data_coverage SET interval_end_utc = :end, checked_at_utc = :checkedAt WHERE id = :id")
    suspend fun extend(id: Long, end: Long, checkedAt: Long)

    @Query("SELECT * FROM data_coverage WHERE metric_type = :kind ORDER BY interval_end_utc DESC LIMIT 1")
    suspend fun latest(kind: String): DataCoverageEntity?

    @Query("SELECT * FROM data_coverage WHERE metric_type = :kind ORDER BY interval_start_utc")
    suspend fun allOfKind(kind: String): List<DataCoverageEntity>

    @Query("SELECT * FROM data_coverage ORDER BY id")
    suspend fun all(): List<DataCoverageEntity>

    @Query("SELECT MIN(interval_start_utc) FROM data_coverage WHERE metric_type = :kind")
    suspend fun earliestStart(kind: String): Long?

    @Query("SELECT COUNT(*) FROM data_coverage")
    suspend fun count(): Int

    @Query("DELETE FROM data_coverage")
    suspend fun deleteAll()
}

@Dao
interface MetaDao {
    @Upsert
    suspend fun upsertProfile(profile: DeviceProfileEntity)

    @Query("SELECT * FROM device_profile WHERE id = 1")
    suspend fun profile(): DeviceProfileEntity?

    @Query("SELECT * FROM device_profile WHERE id = 1")
    fun profileFlow(): Flow<DeviceProfileEntity?>

    @Upsert
    suspend fun upsertCheckpoint(cp: CollectionCheckpointEntity)

    @Query("SELECT * FROM collection_checkpoint WHERE collector_name = :name")
    suspend fun checkpoint(name: String): CollectionCheckpointEntity?

    @Query("SELECT * FROM collection_checkpoint WHERE collector_name = :name")
    fun checkpointFlow(name: String): Flow<CollectionCheckpointEntity?>

    @Query("SELECT * FROM collection_checkpoint")
    suspend fun allCheckpoints(): List<CollectionCheckpointEntity>

    @Upsert
    suspend fun upsertApps(apps: List<AppIdentityEntity>)

    @Query("SELECT * FROM app_identity")
    suspend fun allApps(): List<AppIdentityEntity>

    @Query("SELECT * FROM app_identity WHERE package_name IN (:pkgs)")
    suspend fun apps(pkgs: List<String>): List<AppIdentityEntity>

    @Insert
    suspend fun insertBackupMetadata(meta: BackupMetadataEntity)

    @Query("SELECT * FROM backup_metadata ORDER BY created_at_utc DESC LIMIT 20")
    fun recentBackups(): Flow<List<BackupMetadataEntity>>

    @Query("DELETE FROM device_profile")
    suspend fun deleteProfile()

    @Query("DELETE FROM collection_checkpoint")
    suspend fun deleteCheckpoints()

    @Query("DELETE FROM app_identity")
    suspend fun deleteApps()

    @Query("DELETE FROM backup_metadata")
    suspend fun deleteBackupMetadata()
}
