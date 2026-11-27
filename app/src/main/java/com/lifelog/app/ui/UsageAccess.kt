package com.lifelog.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Opens the system Usage Access screen. Settings.ACTION_USAGE_ACCESS_SETTINGS may be missing on some builds,
 * so we fall back to this app's details page, then to general Settings.
 */
fun openUsageAccessSettings(context: Context) {
    val attempts = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (i in attempts) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}
