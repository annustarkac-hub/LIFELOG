package com.lifelog.app.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.lifelog.app.domain.RawUsageEvent
import com.lifelog.app.domain.UsageEventCodes
import java.util.concurrent.ConcurrentHashMap

/** Usage Access is AppOps-gated special access, checked fresh every call (it can be revoked at any time). */
class AndroidUsageStatsSource(context: Context) : UsageStatsSource {
    private val app = context.applicationContext
    private val usm = app.getSystemService(UsageStatsManager::class.java)
    private val appOps = app.getSystemService(AppOpsManager::class.java)

    override fun hasUsageAccess(): Boolean {
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        return UsageAccessMode.isGranted(mode, app.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
    }

    override fun queryEvents(begin: Long, end: Long): List<RawUsageEvent>? {
        if (!hasUsageAccess() || end <= begin) return null
        val events = runCatching { usm.queryEvents(begin, end) }.getOrNull() ?: return null
        val out = ArrayList<RawUsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(e)) break
            if (e.eventType in RELEVANT) {
                out += RawUsageEvent(e.timeStamp, e.eventType, e.packageName, e.className)
            }
        }
        return out
    }

    override fun queryAppBuckets(interval: BucketInterval, begin: Long, end: Long): List<AppBucket>? {
        if (!hasUsageAccess()) return null
        val stats = runCatching { usm.queryUsageStats(interval.androidConstant, begin, end) }.getOrNull() ?: return null
        return stats.filter { it.totalTimeInForeground > 0 }.map {
            AppBucket(it.packageName, it.firstTimeStamp, it.lastTimeStamp, it.totalTimeInForeground, it.lastTimeUsed)
        }
    }

    override fun queryScreenBuckets(interval: BucketInterval, begin: Long, end: Long): List<ScreenBucket>? {
        if (!hasUsageAccess()) return null
        val stats = runCatching { usm.queryEventStats(interval.androidConstant, begin, end) }.getOrNull() ?: return null
        return stats.filter { it.eventType == UsageEventCodes.SCREEN_INTERACTIVE }.map {
            ScreenBucket(it.firstTimeStamp, it.lastTimeStamp, it.totalTime, it.count)
        }
    }

    companion object {
        private val RELEVANT = setOf(
            UsageEventCodes.ACTIVITY_RESUMED, UsageEventCodes.ACTIVITY_PAUSED, UsageEventCodes.ACTIVITY_STOPPED,
            UsageEventCodes.SCREEN_INTERACTIVE, UsageEventCodes.SCREEN_NON_INTERACTIVE,
            UsageEventCodes.KEYGUARD_SHOWN, UsageEventCodes.KEYGUARD_HIDDEN,
            UsageEventCodes.DEVICE_SHUTDOWN, UsageEventCodes.DEVICE_STARTUP,
        )
    }
}

/** Pure mapping of AppOps modes, unit-tested. MODE_DEFAULT defers to the manifest permission grant state. */
object UsageAccessMode {
    const val MODE_ALLOWED = 0
    const val MODE_IGNORED = 1
    const val MODE_ERRORED = 2
    const val MODE_DEFAULT = 3

    fun isGranted(mode: Int, permissionGranted: Boolean): Boolean = when (mode) {
        MODE_ALLOWED -> true
        MODE_DEFAULT -> permissionGranted
        else -> false
    }
}

/** Labels/icons subject to package visibility (<queries> launcher intent only). */
class AndroidAppInfoSource(context: Context) : AppInfoSource {
    private val pm = context.applicationContext.packageManager
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    override fun info(packageName: String): AppInfo = try {
        val ai = pm.getApplicationInfo(packageName, 0)
        AppInfo(packageName, pm.getApplicationLabel(ai).toString(), installed = true)
    } catch (_: PackageManager.NameNotFoundException) {
        // Either uninstalled or not visible to LIFELOG; we cannot distinguish, so no label is claimed.
        AppInfo(packageName, null, installed = false)
    }

    fun icon(packageName: String, sizePx: Int = 96): ImageBitmap? {
        iconCache[packageName]?.let { return it }
        return try {
            val d = pm.getApplicationIcon(packageName)
            val bmp = createBitmap(sizePx, sizePx)
            val c = Canvas(bmp)
            d.setBounds(0, 0, sizePx, sizePx)
            d.draw(c)
            bmp.asImageBitmap().also { iconCache[packageName] = it }
        } catch (_: Exception) {
            null
        }
    }

    fun launcherVisibleCount(): Int = runCatching {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.distinct().size
    }.getOrDefault(-1)
}

/** Reads the sticky ACTION_BATTERY_CHANGED broadcast: a point-in-time sample, not a continuous history. */
class AndroidBatterySource(context: Context) : BatterySource {
    private val app = context.applicationContext

    override fun read(): BatteryReading? {
        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> null
        }
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            0 -> "UNPLUGGED"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            else -> null
        }
        return BatteryReading(pct, status, plugged)
    }
}

class AndroidDeviceInfoSource(context: Context) : DeviceInfoSource {
    private val pm = context.applicationContext.getSystemService(PowerManager::class.java)
    override fun read() = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
    override fun isInteractive(): Boolean = pm.isInteractive
}
