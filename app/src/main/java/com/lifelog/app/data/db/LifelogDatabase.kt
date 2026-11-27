package com.lifelog.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema version history (exported JSON in app/schemas):
 *  1 — initial schema (2026-11-27).
 * Migrations MUST be added for every future version. fallbackToDestructiveMigration is intentionally NOT used:
 * a failed migration crashes rather than silently wiping user history.
 */
@Database(
    entities = [
        AppIdentityEntity::class,
        UsageRecordEntity::class,
        SystemEventEntity::class,
        BatterySnapshotEntity::class,
        DataCoverageEntity::class,
        DeviceProfileEntity::class,
        CollectionCheckpointEntity::class,
        BackupMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LifelogDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun systemEventDao(): SystemEventDao
    abstract fun batteryDao(): BatteryDao
    abstract fun coverageDao(): CoverageDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "lifelog.db"

        fun build(context: Context): LifelogDatabase =
            Room.databaseBuilder(context.applicationContext, LifelogDatabase::class.java, NAME)
                .build()
    }
}
