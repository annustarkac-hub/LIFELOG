package com.lifelog.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_identity")
data class AppIdentityEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long?,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long?,
    @ColumnInfo(name = "is_currently_installed") val isCurrentlyInstalled: Boolean,
)

/**
 * Normalised usage record with full provenance. Event-derived intervals (APP_FOREGROUND, DEVICE_SCREEN_ON) and
 * Android aggregate buckets (API_*) share this table but are never mixed in calculations (see Analytics).
 */
@Entity(
    tableName = "usage_record",
    indices = [
        Index(value = ["package_name", "start_at_utc"]),
        Index(value = ["metric_type", "start_at_utc"]),
        Index(value = ["dedupe_key"], unique = true),
    ],
)
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String?,
    @ColumnInfo(name = "metric_type") val metricType: String,
    @ColumnInfo(name = "start_at_utc") val startAtUtc: Long,
    @ColumnInfo(name = "end_at_utc") val endAtUtc: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_api") val sourceApi: String,
    @ColumnInfo(name = "source_query_start_utc") val sourceQueryStartUtc: Long,
    @ColumnInfo(name = "source_query_end_utc") val sourceQueryEndUtc: Long,
    @ColumnInfo(name = "collected_at_utc") val collectedAtUtc: Long,
    @ColumnInfo(name = "is_historical") val isHistorical: Boolean,
    @ColumnInfo(name = "is_inferred") val isInferred: Boolean = false,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
)

@Entity(
    tableName = "system_event",
    indices = [
        Index(value = ["event_time_utc"]),
        Index(value = ["event_type", "event_time_utc"]),
        Index(value = ["dedupe_key"], unique = true),
    ],
)
data class SystemEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "event_time_utc") val eventTimeUtc: Long,
    @ColumnInfo(name = "package_name") val packageName: String?,
    @ColumnInfo(name = "value_text") val valueText: String?,
    @ColumnInfo(name = "value_number") val valueNumber: Double?,
    @ColumnInfo(name = "source_api") val sourceApi: String,
    @ColumnInfo(name = "source_event_type") val sourceEventType: Int,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "collected_at_utc") val collectedAtUtc: Long,
)

@Entity(
    tableName = "battery_snapshot",
    indices = [Index(value = ["captured_at_utc"], unique = true)],
)
data class BatterySnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "captured_at_utc") val capturedAtUtc: Long,
    @ColumnInfo(name = "battery_level_percent") val batteryLevelPercent: Int?,
    @ColumnInfo(name = "charging_state") val chargingState: String?,
    @ColumnInfo(name = "plugged_type") val pluggedType: String?,
    @ColumnInfo(name = "source_api") val sourceApi: String,
)

@Entity(
    tableName = "data_coverage",
    indices = [Index(value = ["metric_type", "interval_start_utc"])],
)
data class DataCoverageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** CoverageKind name (EVENTS or NOT_GRANTED). */
    @ColumnInfo(name = "metric_type") val metricType: String,
    @ColumnInfo(name = "interval_start_utc") val intervalStartUtc: Long,
    @ColumnInfo(name = "interval_end_utc") val intervalEndUtc: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "checked_at_utc") val checkedAtUtc: Long,
    @ColumnInfo(name = "notes") val notes: String?,
)

@Entity(tableName = "device_profile")
data class DeviceProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val manufacturer: String,
    val model: String,
    @ColumnInfo(name = "android_release") val androidRelease: String,
    @ColumnInfo(name = "api_level") val apiLevel: Int,
    @ColumnInfo(name = "first_lifelog_tracking_at_utc") val firstLifelogTrackingAtUtc: Long?,
    /** ISO-8601 local date entered by the user. Never verified. */
    @ColumnInfo(name = "user_purchase_date") val userPurchaseDate: String?,
    @ColumnInfo(name = "purchase_date_source") val purchaseDateSource: String?,
    @ColumnInfo(name = "last_successful_collection_at_utc") val lastSuccessfulCollectionAtUtc: Long?,
) {
    companion object {
        const val SINGLETON_ID = 1
        const val PURCHASE_SOURCE_USER = "USER_ENTERED"
    }
}

@Entity(tableName = "collection_checkpoint")
data class CollectionCheckpointEntity(
    @PrimaryKey @ColumnInfo(name = "collector_name") val collectorName: String,
    @ColumnInfo(name = "last_successful_query_end_utc") val lastSuccessfulQueryEndUtc: Long?,
    @ColumnInfo(name = "last_successful_run_at_utc") val lastSuccessfulRunAtUtc: Long?,
    /** Earliest start of an interval still open at the last query end; next query must start at or before it. */
    @ColumnInfo(name = "open_since_utc") val openSinceUtc: Long?,
    @ColumnInfo(name = "last_attempt_at_utc") val lastAttemptAtUtc: Long?,
    @ColumnInfo(name = "last_summary_refresh_utc") val lastSummaryRefreshUtc: Long?,
    val status: String,
    @ColumnInfo(name = "error_code") val errorCode: String?,
)

@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "backup_format_version") val backupFormatVersion: Int,
    @ColumnInfo(name = "app_version") val appVersion: String,
    val checksum: String,
    @ColumnInfo(name = "record_counts_json") val recordCountsJson: String,
    @ColumnInfo(name = "encryption_mode") val encryptionMode: String,
    /** "EXPORT" or "RESTORE_MERGE"/"RESTORE_REPLACE". */
    val operation: String,
)

/** Lightweight projection for analytics. */
data class IntervalRow(
    @ColumnInfo(name = "package_name") val packageName: String?,
    @ColumnInfo(name = "start_at_utc") val start: Long,
    @ColumnInfo(name = "end_at_utc") val end: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "is_historical") val isHistorical: Boolean,
)
