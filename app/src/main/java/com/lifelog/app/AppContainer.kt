package com.lifelog.app

import android.content.Context
import com.lifelog.app.collect.Collector
import com.lifelog.app.data.AnalyticsService
import com.lifelog.app.data.LifelogRepository
import com.lifelog.app.data.SettingsStore
import com.lifelog.app.data.backup.BackupManager
import com.lifelog.app.data.db.LifelogDatabase
import com.lifelog.app.diagnostics.FeasibilityProbe
import com.lifelog.app.platform.AndroidAppInfoSource
import com.lifelog.app.platform.AndroidBatterySource
import com.lifelog.app.platform.AndroidDeviceInfoSource
import com.lifelog.app.platform.AndroidUsageStatsSource
import com.lifelog.app.platform.Clock
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container (ADR-004: no DI framework for a single-module app). */
class AppContainer(private val context: Context) {
    val appContext: Context get() = context.applicationContext
    val clock: Clock = Clock.SYSTEM

    /** Incremented after any write (collection, restore, delete) so screens recompute from the database. */
    val dataVersion = MutableStateFlow(0L)
    fun bumpData() { dataVersion.value = dataVersion.value + 1 }
    val db: LifelogDatabase by lazy { LifelogDatabase.build(context) }
    val repo by lazy { LifelogRepository(db) }
    val settings by lazy { SettingsStore(context) }
    val usageSource by lazy { AndroidUsageStatsSource(context) }
    val appInfo by lazy { AndroidAppInfoSource(context) }
    val batterySource by lazy { AndroidBatterySource(context) }
    val deviceInfo by lazy { AndroidDeviceInfoSource(context) }
    val analytics by lazy { AnalyticsService(repo, clock) }
    val backup by lazy { BackupManager(repo, clock, BuildConfig.VERSION_NAME) }
    val collector by lazy {
        Collector(repo, usageSource, appInfo, batterySource, deviceInfo, clock) {
            settings.current().let { it.trackingEnabled && it.onboardingAccepted }
        }
    }
    val probe by lazy { FeasibilityProbe(usageSource, appInfo, deviceInfo, batterySource, clock, BuildConfig.VERSION_NAME) }

    /** Deletes all app-owned data: database rows, preferences, cache and temporary files. */
    suspend fun deleteAllLocalData() {
        repo.deleteAll()
        settings.clear()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.filesDir.listFiles()?.filter { it.name.startsWith("tmp_") }?.forEach { it.deleteRecursively() }
        bumpData()
    }
}
