package com.lifelog.app.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.BuildConfig
import com.lifelog.app.data.ThemeMode
import com.lifelog.app.data.TimeFormat
import com.lifelog.app.ui.Fmt
import com.lifelog.app.ui.Load
import com.lifelog.app.ui.MainState
import com.lifelog.app.ui.MainViewModel
import com.lifelog.app.ui.components.AppIcon
import com.lifelog.app.ui.components.Eyebrow
import com.lifelog.app.ui.components.KeyValue
import com.lifelog.app.ui.components.NoticeBanner
import com.lifelog.app.ui.components.SectionCard
import com.lifelog.app.ui.openUsageAccessSettings
import com.lifelog.app.ui.theme.LocalProvenance
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
private fun SettingRow(title: String, body: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun PrivacyScreen(main: MainViewModel, state: MainState, pad: PaddingValues, onVault: () -> Unit) {
    val ctx = LocalContext.current
    val tf = state.settings?.timeFormat ?: TimeFormat.SYSTEM
    val p = LocalProvenance.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                Eyebrow("Status")
                SettingRow("Usage Access", if (state.hasUsageAccess) "Granted" else "Not granted — collection is paused") {
                    TextButton(onClick = { openUsageAccessSettings(ctx) }) { Text(if (state.hasUsageAccess) "Manage" else "Grant") }
                }
                SettingRow("Tracking", if (state.settings?.trackingEnabled != false) "On — LIFELOG reads Android usage data when it runs" else "Paused — no new data is collected") {
                    Switch(state.settings?.trackingEnabled != false, main::setTracking)
                }
                KeyValue("Last successful collection", Fmt.dateTime(state.checkpoint?.lastSuccessfulRunAtUtc, tf))
                KeyValue("Last attempt status", state.checkpoint?.status?.replace('_', ' ')?.lowercase() ?: "—")
            }
        }
        item {
            SectionCard {
                Eyebrow("What is collected")
                Text(
                    "• App foreground/background transitions per app (from Android usage events)\n" +
                        "• Screen interactive/non-interactive and keyguard shown/hidden moments\n" +
                        "• Android runtime startup/shutdown markers\n" +
                        "• Android's daily, monthly and yearly usage summaries\n" +
                        "• Battery level/charging samples taken when LIFELOG runs",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Eyebrow("Never collected")
                Text(
                    "Messages, notification contents, passwords, keystrokes, touches, screen contents, location, contacts, calls, or anything inside other apps. LIFELOG has no Accessibility Service and no internet permission.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard {
                Eyebrow("Limitations")
                Text(
                    "• Android keeps detailed events only for a limited time (documented as \"a few days\"); older periods have at most Android's coarser summaries.\n" +
                        "• Background collection is inexact: Doze, battery optimisation, force-stop or a restart can delay it. Missed periods are reconciled when Android still has the events, otherwise shown as gaps.\n" +
                        "• Screen-on time is Android-reported interactive time and is never computed from app usage.\n" +
                        "• Some app names/icons are hidden by Android package visibility; those show the package name.\n" +
                        "• The purchase date is whatever you enter. Android does not provide it.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            NoticeBanner(
                "Storage", "All data is stored in LIFELOG's private app storage on this phone and excluded from Android cloud backup. Uninstall or factory reset deletes it unless you export a backup.",
                p.recorded,
            ) { TextButton(onClick = onVault) { Text("Export or delete data in the Vault") } }
        }
        item {
            SectionCard {
                Eyebrow("Privacy policy")
                Text(PRIVACY_POLICY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

const val PRIVACY_POLICY = "LIFELOG processes device-usage statistics that Android provides after you grant Usage Access. " +
    "Data is stored only on your device, is not transmitted to the developer or any third party, and contains no advertising or analytics SDKs. " +
    "You can pause tracking, revoke Usage Access, export your data, or delete all data at any time from within the app. " +
    "Backups you export are files you control; encrypted backups use a password only you know."

@Composable
fun SettingsScreen(main: MainViewModel, state: MainState, pad: PaddingValues, onExclusions: () -> Unit, onDiagnostics: () -> Unit, onDevice: () -> Unit) {
    val s = state.settings ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                Eyebrow("Appearance")
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { t -> FilterChip(s.theme == t, { main.setTheme(t) }, { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Time format", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TimeFormat.SYSTEM to "System", TimeFormat.H24 to "24-hour", TimeFormat.H12 to "12-hour").forEach { (f, l) ->
                        FilterChip(s.timeFormat == f, { main.setTimeFormat(f) }, { Text(l) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Week starts on", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY).forEach { d ->
                        FilterChip(s.weekStart == d, { main.setWeekStart(d) }, { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
        item {
            SectionCard {
                Eyebrow("Data")
                SettingRow("Excluded apps", "${s.excludedPackages.size} excluded from summaries") { TextButton(onClick = onExclusions) { Text("Edit") } }
                SettingRow("Device overview", "Model, Android version, tracking start, purchase date") { TextButton(onClick = onDevice) { Text("Open") } }
                SettingRow("Data retention", "Kept until you delete it. Raw Android events are not stored; only normalised sessions and markers.") {}
            }
        }
        item {
            SectionCard {
                Eyebrow("Diagnostics")
                SettingRow("Feasibility probe", "Measure what Android returns on this phone (retention, event types, visibility)") { TextButton(onClick = onDiagnostics) { Text("Run") } }
                KeyValue("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
        }
    }
}

@Composable
fun DeviceScreen(main: MainViewModel, state: MainState, pad: PaddingValues) {
    val ctx = LocalContext.current
    val tf = state.settings?.timeFormat ?: TimeFormat.SYSTEM
    val prof = state.profile
    val p = LocalProvenance.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                Eyebrow("Device")
                KeyValue("Manufacturer", prof?.manufacturer ?: android.os.Build.MANUFACTURER)
                KeyValue("Model", prof?.model ?: android.os.Build.MODEL)
                KeyValue("Android", "${prof?.androidRelease ?: android.os.Build.VERSION.RELEASE} (API ${prof?.apiLevel ?: android.os.Build.VERSION.SDK_INT})")
            }
        }
        item {
            SectionCard {
                Eyebrow("LIFELOG")
                KeyValue("Tracking status", when {
                    state.settings?.trackingEnabled == false -> "Paused by you"
                    !state.hasUsageAccess -> "Paused — Usage Access not granted"
                    else -> "Active"
                })
                KeyValue("First successful tracking", Fmt.dateTime(prof?.firstLifelogTrackingAtUtc, tf))
                KeyValue("Last successful collection", Fmt.dateTime(prof?.lastSuccessfulCollectionAtUtc, tf))
            }
        }
        item {
            SectionCard {
                Eyebrow("Purchase date · user-provided (unverified)", p.partial)
                KeyValue("Purchase date", prof?.userPurchaseDate?.let { Fmt.date(LocalDate.parse(it)) + " (you entered this)" } ?: "Not set")
                Text(
                    "Android does not give apps a verified purchase date. Manufacturing, build or first-boot dates are not purchase dates, so LIFELOG never guesses it. This value is not used in any calculation.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val init = prof?.userPurchaseDate?.let(LocalDate::parse) ?: LocalDate.now()
                        DatePickerDialog(ctx, { _, y, m, d -> main.setPurchaseDate(LocalDate.of(y, m + 1, d)) }, init.year, init.monthValue - 1, init.dayOfMonth)
                            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }) { Text(if (prof?.userPurchaseDate == null) "Enter date" else "Change") }
                    if (prof?.userPurchaseDate != null) TextButton(onClick = { main.setPurchaseDate(null) }) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(main: MainViewModel, pad: PaddingValues) {
    val probe by main.probe.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val text = (probe as? Load.Ready)?.data
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && text != null) runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } }
    }
    LaunchedEffect(Unit) { if (probe == null) main.runProbe() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Measures what Android actually returns on this phone. The report contains counts and dates only, not which apps you used.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = main::runProbe) { Text("Run again") }
                OutlinedButton(onClick = { save.launch("lifelog-feasibility-${LocalDate.now()}.txt") }, enabled = text != null) { Text("Save report") }
            }
        }
        item {
            when (val pr = probe) {
                null, Load.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                is Load.Failed -> Text("Probe failed: ${pr.message}", color = MaterialTheme.colorScheme.error)
                is Load.Ready -> SectionCard { Text(pr.data, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
fun ExclusionsScreen(main: MainViewModel, state: MainState, pad: PaddingValues, iconLoader: (String) -> ImageBitmap?) {
    val apps by main.knownApps.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { main.loadKnownApps() }
    val excluded = state.settings?.excludedPackages ?: emptySet()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        item {
            Text(
                "Excluded apps are left out of totals and charts. This does not change any Android permission and does not delete their records.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(apps, key = { it.packageName }) { a ->
            val ex = a.packageName in excluded
            Row(
                Modifier.fillMaxWidth().clickable { main.setExcluded(a.packageName, !ex) }.heightIn(min = 56.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(a.packageName, a.displayName, iconLoader)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.displayName ?: a.packageName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(ex, { main.setExcluded(a.packageName, it) })
            }
        }
    }
}
