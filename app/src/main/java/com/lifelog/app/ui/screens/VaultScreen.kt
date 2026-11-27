package com.lifelog.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lifelog.app.AppContainer
import com.lifelog.app.data.backup.BackupCodec
import com.lifelog.app.data.backup.BackupEnvelope
import com.lifelog.app.data.backup.BackupException
import com.lifelog.app.data.backup.CsvRow
import com.lifelog.app.data.backup.RestoreMode
import com.lifelog.app.data.backup.ValidatedBackup
import com.lifelog.app.data.db.BackupMetadataEntity
import com.lifelog.app.ui.Fmt
import com.lifelog.app.ui.components.Eyebrow
import com.lifelog.app.ui.components.KeyValue
import com.lifelog.app.ui.components.NoticeBanner
import com.lifelog.app.ui.components.SectionCard
import com.lifelog.app.ui.theme.LocalProvenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

sealed interface VaultUi {
    data object Idle : VaultUi
    data class Busy(val what: String) : VaultUi
    data class Message(val title: String, val body: String, val error: Boolean = false) : VaultUi
    data class NeedPassword(val uri: Uri, val envelope: BackupEnvelope, val wrong: Boolean = false) : VaultUi
    data class ConfirmRestore(val backup: ValidatedBackup) : VaultUi
}

class VaultViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow<VaultUi>(VaultUi.Idle)
    val ui = _ui.asStateFlow()
    val history = c.repo.meta.recentBackups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<BackupMetadataEntity>())
    private val resolver get() = c.appContext.contentResolver

    fun dismiss() { _ui.value = VaultUi.Idle }

    fun exportJson(uri: Uri, password: String?) = viewModelScope.launch {
        _ui.value = VaultUi.Busy("Writing backup…")
        _ui.value = try {
            val counts = resolver.openOutputStream(uri, "wt")?.use { c.backup.export(it, password?.takeIf { p -> p.isNotEmpty() }?.toCharArray()) }
                ?: error("Could not open the selected file")
            VaultUi.Message("Backup saved", "Records: ${counts.entries.joinToString { "${it.key} ${it.value}" }}.\n" +
                (if (password.isNullOrEmpty()) "Not encrypted — store the file somewhere private." else "Encrypted with your password. LIFELOG cannot recover a forgotten password."))
        } catch (e: Exception) {
            VaultUi.Message("Backup failed", e.message ?: e.javaClass.simpleName, true)
        }
    }

    fun inspect(uri: Uri, password: String? = null) = viewModelScope.launch {
        _ui.value = VaultUi.Busy("Validating backup…")
        var env: BackupEnvelope? = null
        _ui.value = try {
            val (text, size) = resolver.openInputStream(uri)?.let { c.backup.readText(it) } ?: error("Could not open the selected file")
            env = BackupCodec.readEnvelope(text, size)
            if (env.encryption != BackupCodec.ENC_NONE && password == null) VaultUi.NeedPassword(uri, env)
            else VaultUi.ConfirmRestore(BackupCodec.decodeAndValidate(text, size, password?.toCharArray()))
        } catch (e: BackupException) {
            val known = env
            if (e.reason == BackupException.Reason.WRONG_PASSWORD_OR_TAMPERED && password != null && known != null) {
                VaultUi.NeedPassword(uri, known, wrong = true)
            } else VaultUi.Message("Restore rejected", e.message ?: e.reason.name, true)
        } catch (e: Exception) {
            VaultUi.Message("Restore rejected", e.message ?: e.javaClass.simpleName, true)
        }
    }

    fun passwordFor(uri: Uri, password: String) {
        inspect(uri, password)
    }

    fun apply(backup: ValidatedBackup, mode: RestoreMode) = viewModelScope.launch {
        _ui.value = VaultUi.Busy("Restoring…")
        _ui.value = try {
            val r = c.backup.apply(backup, mode)
            c.bumpData()
            VaultUi.Message("Restore complete (${mode.name.lowercase()})", "Inserted: ${r.inserted.entries.joinToString { "${it.key} ${it.value}" }}. Existing records were ${if (mode == RestoreMode.MERGE) "kept" else "replaced"}.")
        } catch (e: Exception) {
            VaultUi.Message("Restore failed — nothing was changed", e.message ?: e.javaClass.simpleName, true)
        }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        _ui.value = VaultUi.Busy("Writing CSV…")
        _ui.value = try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val since = c.analytics.earliestDataDate(zone) ?: today
            val excluded = c.settings.current().excludedPackages
            val days = c.analytics.days(since, today, zone, excluded)
            val apps = c.analytics.apps()
            val rows = ArrayList<CsvRow>()
            for (d in days) {
                val src = d.source.name
                d.screenOnMs?.let { rows += CsvRow(d.date.toString(), "DEVICE_SCREEN_ON", "", "", it, null, src, d.coverage.name) }
                d.anyAppForegroundMs?.let { rows += CsvRow(d.date.toString(), "ANY_APP_FOREGROUND", "", "", it, null, src, d.coverage.name) }
                d.perAppMs.entries.sortedByDescending { it.value }.forEach { (pkg, ms) ->
                    rows += CsvRow(d.date.toString(), "APP_FOREGROUND", pkg, apps[pkg]?.displayName ?: "", ms, d.perAppEntries[pkg], src, d.coverage.name)
                }
            }
            resolver.openOutputStream(uri, "wt")?.use { c.backup.exportCsv(it, rows) } ?: error("Could not open the selected file")
            VaultUi.Message("CSV exported", "${rows.size} rows from ${Fmt.date(since)} to ${Fmt.date(today)}.")
        } catch (e: Exception) {
            VaultUi.Message("CSV export failed", e.message ?: e.javaClass.simpleName, true)
        }
    }

    fun deleteAll() = viewModelScope.launch {
        _ui.value = VaultUi.Busy("Deleting…")
        _ui.value = try {
            c.deleteAllLocalData()
            VaultUi.Message("All local data deleted", "Database records, preferences and cached files were removed. You will see onboarding again.")
        } catch (e: Exception) {
            VaultUi.Message("Delete failed", e.message ?: e.javaClass.simpleName, true)
        }
    }
}

@Composable
fun VaultScreen(vm: VaultViewModel, pad: PaddingValues) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val stamp = LocalDate.now().toString()
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.exportJson(u, password) } }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.inspect(u) } }
    val p = LocalProvenance.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NoticeBanner(
                "Your data lives only on this phone",
                "Uninstalling LIFELOG or resetting the phone erases it. Export a backup to a location you control (e.g. a folder you copy to a computer).",
                p.partial,
            )
        }
        item {
            SectionCard {
                Eyebrow("Full backup · JSON")
                Text("All records with provenance, versioned and checksummed (SHA-256).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password (optional, min 8 characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Encrypts with AES-256-GCM. A forgotten password cannot be recovered.") },
                    isError = password.isNotEmpty() && password.length < 8,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { createJson.launch("lifelog-backup-$stamp.json") },
                    enabled = password.isEmpty() || password.length >= 8,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (password.isEmpty()) "Export unencrypted backup" else "Export encrypted backup") }
            }
        }
        item {
            SectionCard {
                Eyebrow("Restore")
                Text("The file is validated (format, version, checksum, every record) before anything changes. You then choose Merge or Replace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Choose backup file…")
                }
            }
        }
        item {
            SectionCard {
                Eyebrow("Summary · CSV")
                Text("Daily totals per app and device screen-on, with source and coverage columns.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { createCsv.launch("lifelog-summary-$stamp.csv") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Export CSV summary") }
            }
        }
        if (history.isNotEmpty()) {
            item {
                SectionCard {
                    Eyebrow("Recent backup activity")
                    history.take(8).forEach {
                        KeyValue(Fmt.dateTime(it.createdAtUtc, com.lifelog.app.data.TimeFormat.SYSTEM), "${it.operation.replace('_', ' ').lowercase()} · ${if (it.encryptionMode == "NONE") "unencrypted" else "encrypted"}")
                    }
                }
            }
        }
        item {
            SectionCard {
                Eyebrow("Danger zone", MaterialTheme.colorScheme.error)
                Text("Permanently delete every record LIFELOG stored on this phone. Export a backup first if you may want it later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { confirmDelete = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Delete all local data") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all LIFELOG data?") },
            text = { Text("This removes all usage records, events, coverage, settings and cached files. It cannot be undone. Android's own usage history is not affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAll() }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    when (val u = ui) {
        VaultUi.Idle -> Unit
        is VaultUi.Busy -> AlertDialog({}, confirmButton = {}, title = { Text(u.what) }, text = { Text("Please keep LIFELOG open.") })
        is VaultUi.Message -> AlertDialog(
            onDismissRequest = vm::dismiss, title = { Text(u.title) }, text = { Text(u.body) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("OK") } },
        )
        is VaultUi.NeedPassword -> {
            var pw by remember(u.uri) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text("Backup is password protected") },
                text = {
                    Column {
                        Text("Created ${Fmt.dateTime(u.envelope.createdAtUtc, com.lifelog.app.data.TimeFormat.SYSTEM)} by LIFELOG ${u.envelope.appVersion}.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), isError = u.wrong,
                            supportingText = { if (u.wrong) Text("Wrong password, or the file was modified.") })
                    }
                },
                confirmButton = { TextButton(onClick = { vm.passwordFor(u.uri, pw) }, enabled = pw.isNotEmpty()) { Text("Unlock") } },
                dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancel") } },
            )
        }
        is VaultUi.ConfirmRestore -> {
            var replaceConfirm by remember { mutableStateOf(false) }
            val env = u.backup.envelope
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text(if (replaceConfirm) "Replace all current data?" else "Backup is valid") },
                text = {
                    Column {
                        if (replaceConfirm) {
                            Text("Every record currently on this phone will be deleted and replaced by the backup. If anything fails, nothing is changed.")
                        } else {
                            Text("Created ${Fmt.dateTime(env.createdAtUtc, com.lifelog.app.data.TimeFormat.SYSTEM)} · app ${env.appVersion} · format v${env.formatVersion}")
                            Spacer(Modifier.height(6.dp))
                            env.recordCounts.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(8.dp))
                            Text("Merge keeps your current data and adds records that are not already present. Replace deletes current data first.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    if (replaceConfirm) TextButton(onClick = { vm.apply(u.backup, RestoreMode.REPLACE) }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
                    else TextButton(onClick = { vm.apply(u.backup, RestoreMode.MERGE) }) { Text("Merge") }
                },
                dismissButton = {
                    if (replaceConfirm) TextButton(onClick = { replaceConfirm = false }) { Text("Back") }
                    else Column {
                        TextButton(onClick = { replaceConfirm = true }) { Text("Replace…") }
                        TextButton(onClick = vm::dismiss) { Text("Cancel") }
                    }
                },
            )
        }
    }
}
