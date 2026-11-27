package com.lifelog.app.data.backup

import com.lifelog.app.data.LifelogRepository
import com.lifelog.app.data.db.BackupMetadataEntity
import com.lifelog.app.data.db.DeviceProfileEntity
import com.lifelog.app.platform.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

enum class RestoreMode { MERGE, REPLACE }

private fun countsJson(m: Map<String, Int>): String = m.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

data class RestoreResult(val mode: RestoreMode, val inserted: Map<String, Int>)

class BackupManager(private val repo: LifelogRepository, private val clock: Clock, private val appVersion: String) {

    suspend fun buildPayload(): BackupPayload = withContext(Dispatchers.IO) {
        BackupPayload(
            appIdentity = repo.meta.allApps().map { it.toDto() },
            usageRecords = repo.usage.all().map { it.toDto() },
            systemEvents = repo.events.all().map { it.toDto() },
            batterySnapshots = repo.battery.all().map { it.toDto() },
            coverage = repo.coverage.all().map { it.toDto() },
            deviceProfile = repo.profile()?.toDto(),
        )
    }

    suspend fun export(out: OutputStream, password: CharArray?): Map<String, Int> = withContext(Dispatchers.IO) {
        val payload = buildPayload()
        val now = clock.now()
        val text = BackupCodec.encode(payload, appVersion, now, password)
        out.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        val counts = BackupCodec.counts(payload)
        val env = BackupCodec.readEnvelope(text, text.length.toLong())
        repo.meta.insertBackupMetadata(
            BackupMetadataEntity(0, now, BackupCodec.FORMAT_VERSION, appVersion, env.payloadSha256, countsJson(counts),
                env.encryption, "EXPORT")
        )
        counts
    }

    /** Reads at most MAX_BYTES + 1 so oversize files are rejected without loading them fully. */
    fun readText(input: InputStream): Pair<String, Long> {
        val bytes = input.use { s ->
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = s.read(chunk)
                if (n < 0) break
                total += n
                if (total > BackupCodec.MAX_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE, "Backup exceeds size limit")
                buf.write(chunk, 0, n)
            }
            buf.toByteArray()
        }
        return String(bytes, Charsets.UTF_8) to bytes.size.toLong()
    }

    /**
     * Applies an already validated backup in ONE transaction; any failure rolls back and leaves current data intact.
     * MERGE: existing rows win on dedupe-key conflicts; device profile keeps local values, adopting backup values only
     * where local ones are empty, and the earlier first-tracking time.
     * REPLACE: deletes all local rows first, then inserts the backup.
     */
    suspend fun apply(backup: ValidatedBackup, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        val p = backup.payload
        val inserted = repo.transaction {
            if (mode == RestoreMode.REPLACE) {
                repo.usage.deleteAll(); repo.events.deleteAll(); repo.battery.deleteAll(); repo.coverage.deleteAll()
                repo.meta.deleteApps(); repo.meta.deleteCheckpoints(); repo.meta.deleteProfile()
            }
            val rec = p.usageRecords.map { it.toEntity() }.chunked(500).sumOf { c -> repo.usage.insertRecordsIgnore(c).count { it != -1L } }
            val ev = p.systemEvents.map { it.toEntity() }.chunked(500).sumOf { c -> repo.events.insertIgnore(c).count { it != -1L } }
            val bat = repo.battery.insert(p.batterySnapshots.map { it.toEntity() }).count { it != -1L }
            // Coverage rows are interval claims; duplicates are harmless because analytics unions them.
            repo.coverage.insertAll(p.coverage.map { it.toEntity() })
            val localApps = repo.meta.allApps().map { it.packageName }.toSet()
            val apps = p.appIdentity.filter { it.packageName !in localApps }.map { it.toEntity() }
            repo.meta.upsertApps(apps)
            p.deviceProfile?.toEntity()?.let { b ->
                val local = repo.profile()
                val merged = if (local == null) b else local.copy(
                    firstLifelogTrackingAtUtc = listOfNotNull(local.firstLifelogTrackingAtUtc, b.firstLifelogTrackingAtUtc).minOrNull(),
                    userPurchaseDate = local.userPurchaseDate ?: b.userPurchaseDate,
                    purchaseDateSource = local.purchaseDateSource ?: b.purchaseDateSource,
                    lastSuccessfulCollectionAtUtc = listOfNotNull(local.lastSuccessfulCollectionAtUtc, b.lastSuccessfulCollectionAtUtc).maxOrNull(),
                )
                repo.saveProfile(merged.copy(id = DeviceProfileEntity.SINGLETON_ID))
            }
            repo.meta.insertBackupMetadata(
                BackupMetadataEntity(0, clock.now(), backup.envelope.formatVersion, backup.envelope.appVersion, backup.envelope.payloadSha256,
                    countsJson(backup.envelope.recordCounts), backup.envelope.encryption, "RESTORE_${mode.name}")
            )
            mapOf("usageRecords" to rec, "systemEvents" to ev, "batterySnapshots" to bat, "coverage" to p.coverage.size, "appIdentity" to apps.size)
        }
        RestoreResult(mode, inserted)
    }

    /** Human-readable daily summary CSV (per app and device screen-on), with provenance columns. */
    suspend fun exportCsv(out: OutputStream, rows: List<CsvRow>) = withContext(Dispatchers.IO) {
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write(CsvExporter.HEADER); w.write("\n")
            rows.forEach { w.write(CsvExporter.line(it)); w.write("\n") }
        }
    }

}

data class CsvRow(
    val localDate: String,
    val metric: String,
    val packageName: String,
    val appLabel: String,
    val durationMs: Long,
    val entries: Int?,
    val source: String,
    val coverage: String,
)

object CsvExporter {
    const val HEADER = "local_date,metric,package_name,app_label,duration_ms,duration_hms,foreground_entries,source,coverage"

    fun line(r: CsvRow): String = listOf(
        r.localDate, r.metric, r.packageName, r.appLabel, r.durationMs.toString(), hms(r.durationMs),
        r.entries?.toString() ?: "", r.source, r.coverage,
    ).joinToString(",") { escape(it) }

    fun hms(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** RFC 4180 quoting; also neutralises spreadsheet formula injection (=,+,-,@ prefixes). */
    fun escape(v: String): String {
        val safe = if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
