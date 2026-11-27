package com.lifelog.app.collect

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifelog.app.LifelogApp
import java.util.concurrent.TimeUnit

/**
 * Inexact, deferrable reconciliation. WorkManager may run it late (Doze, App Standby, OEM battery managers) or not at
 * all after a force-stop until the app is opened again. Correctness never depends on its timing: each run
 * reconciles from the stored checkpoint. WorkManager reschedules periodic work after reboot by itself.
 */
class CollectionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LifelogApp).container
        return try {
            container.collector.collect()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "lifelog-periodic-collection"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CollectionWorker>(1, TimeUnit.HOURS, 20, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
