package com.lifelog.app.data.backup

import com.lifelog.app.data.db.AppIdentityEntity
import com.lifelog.app.data.db.BatterySnapshotEntity
import com.lifelog.app.data.db.DataCoverageEntity
import com.lifelog.app.data.db.DeviceProfileEntity
import com.lifelog.app.data.db.SystemEventEntity
import com.lifelog.app.data.db.UsageRecordEntity
import com.lifelog.app.domain.CoverageKind
import com.lifelog.app.domain.CoverageStatus
import com.lifelog.app.domain.MetricType
import com.lifelog.app.domain.SourceType
import com.lifelog.app.domain.SystemEventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * LIFELOG backup file, format version 1.
 *
 * Envelope (JSON): format, formatVersion, schemaVersion, appVersion, createdAtUtc, encryption, recordCounts,
 * payloadSha256 (hex SHA-256 of the UTF-8 plaintext payload JSON), and either `payload` (plaintext JSON string) or
 * `ciphertext` + kdf params (AES-256-GCM, key = PBKDF2WithHmacSHA256(password, salt, iterations)).
 */
@Serializable
data class BackupEnvelope(
    val format: String,
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAtUtc: Long,
    val encryption: String,
    val recordCounts: Map<String, Int>,
    val payloadSha256: String,
    val payload: String? = null,
    val ciphertext: String? = null,
    val salt: String? = null,
    val iv: String? = null,
    val iterations: Int? = null,
)

@Serializable
data class BackupPayload(
    val appIdentity: List<AppIdentityDto> = emptyList(),
    val usageRecords: List<UsageRecordDto> = emptyList(),
    val systemEvents: List<SystemEventDto> = emptyList(),
    val batterySnapshots: List<BatteryDto> = emptyList(),
    val coverage: List<CoverageDto> = emptyList(),
    val deviceProfile: DeviceProfileDto? = null,
)

@Serializable data class AppIdentityDto(val packageName: String, val displayName: String?, val firstSeenAt: Long?, val lastSeenAt: Long?, val isCurrentlyInstalled: Boolean)
@Serializable data class UsageRecordDto(
    val packageName: String?, val metricType: String, val startAtUtc: Long, val endAtUtc: Long, val durationMs: Long,
    val sourceType: String, val sourceApi: String, val sourceQueryStartUtc: Long, val sourceQueryEndUtc: Long,
    val collectedAtUtc: Long, val isHistorical: Boolean, val isInferred: Boolean, val dedupeKey: String,
)
@Serializable data class SystemEventDto(
    val eventType: String, val eventTimeUtc: Long, val packageName: String?, val valueText: String?, val valueNumber: Double?,
    val sourceApi: String, val sourceEventType: Int, val dedupeKey: String, val collectedAtUtc: Long,
)
@Serializable data class BatteryDto(val capturedAtUtc: Long, val batteryLevelPercent: Int?, val chargingState: String?, val pluggedType: String?, val sourceApi: String)
@Serializable data class CoverageDto(val metricType: String, val intervalStartUtc: Long, val intervalEndUtc: Long, val status: String, val source: String, val checkedAtUtc: Long, val notes: String?)
@Serializable data class DeviceProfileDto(
    val manufacturer: String, val model: String, val androidRelease: String, val apiLevel: Int,
    val firstLifelogTrackingAtUtc: Long?, val userPurchaseDate: String?, val purchaseDateSource: String?, val lastSuccessfulCollectionAtUtc: Long?,
)

class BackupException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_A_BACKUP, UNSUPPORTED_VERSION, TOO_LARGE, PASSWORD_REQUIRED, WRONG_PASSWORD_OR_TAMPERED, CHECKSUM_MISMATCH, INVALID_RECORDS }
}

data class ValidatedBackup(val envelope: BackupEnvelope, val payload: BackupPayload)

object BackupCodec {
    const val FORMAT = "lifelog-backup"
    const val FORMAT_VERSION = 1
    const val SCHEMA_VERSION = 1
    const val ENC_NONE = "NONE"
    const val ENC_PBKDF2_AES_GCM = "PBKDF2-HMAC-SHA256/AES-256-GCM"
    const val MAX_BYTES = 256L * 1024 * 1024
    private const val ITERATIONS = 210_000
    private const val PASSWORD_MIN = 8

    val json = Json { ignoreUnknownKeys = false; encodeDefaults = true; explicitNulls = true }

    fun encode(payload: BackupPayload, appVersion: String, now: Long, password: CharArray?): String {
        val plain = json.encodeToString(BackupPayload.serializer(), payload)
        val sha = sha256Hex(plain.toByteArray(Charsets.UTF_8))
        val counts = counts(payload)
        val env = if (password == null) {
            BackupEnvelope(FORMAT, FORMAT_VERSION, SCHEMA_VERSION, appVersion, now, ENC_NONE, counts, sha, payload = plain)
        } else {
            require(password.size >= PASSWORD_MIN) { "Password must be at least $PASSWORD_MIN characters" }
            val rnd = SecureRandom()
            val salt = ByteArray(16).also(rnd::nextBytes)
            val iv = ByteArray(12).also(rnd::nextBytes)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
            val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
            val b64 = Base64.getEncoder()
            BackupEnvelope(
                FORMAT, FORMAT_VERSION, SCHEMA_VERSION, appVersion, now, ENC_PBKDF2_AES_GCM, counts, sha,
                ciphertext = b64.encodeToString(ct), salt = b64.encodeToString(salt), iv = b64.encodeToString(iv), iterations = ITERATIONS,
            )
        }
        return json.encodeToString(BackupEnvelope.serializer(), env)
    }

    /** Parses the envelope without decrypting, so the UI can tell whether a password is needed. */
    fun readEnvelope(text: String, sizeBytes: Long): BackupEnvelope {
        if (sizeBytes > MAX_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE, "Backup exceeds ${MAX_BYTES / (1024 * 1024)} MB")
        val env = try {
            json.decodeFromString(BackupEnvelope.serializer(), text)
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Not a LIFELOG backup file")
        }
        if (env.format != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Not a LIFELOG backup file")
        if (env.formatVersion > FORMAT_VERSION || env.formatVersion < 1 || env.schemaVersion > SCHEMA_VERSION) {
            throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION, "Backup format ${env.formatVersion}/schema ${env.schemaVersion} is not supported by this app version")
        }
        return env
    }

    fun decodeAndValidate(text: String, sizeBytes: Long, password: CharArray?): ValidatedBackup {
        val env = readEnvelope(text, sizeBytes)
        val plain = when (env.encryption) {
            ENC_NONE -> env.payload ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Missing payload")
            ENC_PBKDF2_AES_GCM -> {
                if (password == null) throw BackupException(BackupException.Reason.PASSWORD_REQUIRED, "This backup is password protected")
                val d = Base64.getDecoder()
                try {
                    val iter = env.iterations ?: throw IllegalArgumentException()
                    if (iter !in 10_000..5_000_000) throw IllegalArgumentException()
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, deriveKey(password, d.decode(env.salt), iter), GCMParameterSpec(128, d.decode(env.iv)))
                    String(c.doFinal(d.decode(env.ciphertext)), Charsets.UTF_8)
                } catch (e: AEADBadTagException) {
                    throw BackupException(BackupException.Reason.WRONG_PASSWORD_OR_TAMPERED, "Wrong password or the file was modified")
                } catch (e: Exception) {
                    throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Encrypted section is malformed")
                }
            }
            else -> throw BackupException(BackupException.Reason.UNSUPPORTED_VERSION, "Unknown encryption ${env.encryption}")
        }
        if (!MessageDigest.isEqual(sha256Hex(plain.toByteArray(Charsets.UTF_8)).toByteArray(), env.payloadSha256.lowercase().toByteArray())) {
            throw BackupException(BackupException.Reason.CHECKSUM_MISMATCH, "Checksum mismatch: the backup is corrupt or was modified")
        }
        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), plain)
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.INVALID_RECORDS, "Payload does not match schema ${env.schemaVersion}")
        }
        validate(payload)
        if (counts(payload) != env.recordCounts) throw BackupException(BackupException.Reason.INVALID_RECORDS, "Record counts do not match header")
        return ValidatedBackup(env, payload)
    }

    private fun validate(p: BackupPayload) {
        fun fail(msg: String): Nothing = throw BackupException(BackupException.Reason.INVALID_RECORDS, msg)
        val metrics = MetricType.entries.map { it.name }.toSet()
        val sources = SourceType.entries.map { it.name }.toSet()
        p.usageRecords.forEachIndexed { i, r ->
            if (r.metricType !in metrics) fail("usageRecords[$i]: unknown metric ${r.metricType}")
            if (r.sourceType !in sources) fail("usageRecords[$i]: unknown source ${r.sourceType}")
            if (r.endAtUtc < r.startAtUtc || r.durationMs < 0) fail("usageRecords[$i]: invalid interval")
            if (r.dedupeKey.isBlank()) fail("usageRecords[$i]: missing dedupe key")
        }
        val evTypes = SystemEventType.entries.map { it.name }.toSet()
        p.systemEvents.forEachIndexed { i, e -> if (e.eventType !in evTypes || e.dedupeKey.isBlank()) fail("systemEvents[$i]: invalid") }
        val kinds = CoverageKind.entries.map { it.name }.toSet()
        val statuses = CoverageStatus.entries.map { it.name }.toSet()
        p.coverage.forEachIndexed { i, c ->
            if (c.metricType !in kinds || c.status !in statuses || c.intervalEndUtc < c.intervalStartUtc) fail("coverage[$i]: invalid")
        }
        p.batterySnapshots.forEachIndexed { i, b -> if (b.batteryLevelPercent != null && b.batteryLevelPercent !in 0..100) fail("batterySnapshots[$i]: invalid level") }
        p.appIdentity.forEachIndexed { i, a -> if (a.packageName.isBlank() || a.packageName.length > 255) fail("appIdentity[$i]: invalid package") }
    }

    fun counts(p: BackupPayload) = mapOf(
        "appIdentity" to p.appIdentity.size,
        "usageRecords" to p.usageRecords.size,
        "systemEvents" to p.systemEvents.size,
        "batterySnapshots" to p.batterySnapshots.size,
        "coverage" to p.coverage.size,
        "deviceProfile" to if (p.deviceProfile == null) 0 else 1,
    )

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

// ---- entity <-> dto mappers ----
fun AppIdentityEntity.toDto() = AppIdentityDto(packageName, displayName, firstSeenAt, lastSeenAt, isCurrentlyInstalled)
fun AppIdentityDto.toEntity() = AppIdentityEntity(packageName, displayName, firstSeenAt, lastSeenAt, isCurrentlyInstalled)
fun UsageRecordEntity.toDto() = UsageRecordDto(packageName, metricType, startAtUtc, endAtUtc, durationMs, sourceType, sourceApi, sourceQueryStartUtc, sourceQueryEndUtc, collectedAtUtc, isHistorical, isInferred, dedupeKey)
fun UsageRecordDto.toEntity() = UsageRecordEntity(0, packageName, metricType, startAtUtc, endAtUtc, durationMs, sourceType, sourceApi, sourceQueryStartUtc, sourceQueryEndUtc, collectedAtUtc, isHistorical, isInferred, dedupeKey)
fun SystemEventEntity.toDto() = SystemEventDto(eventType, eventTimeUtc, packageName, valueText, valueNumber, sourceApi, sourceEventType, dedupeKey, collectedAtUtc)
fun SystemEventDto.toEntity() = SystemEventEntity(0, eventType, eventTimeUtc, packageName, valueText, valueNumber, sourceApi, sourceEventType, dedupeKey, collectedAtUtc)
fun BatterySnapshotEntity.toDto() = BatteryDto(capturedAtUtc, batteryLevelPercent, chargingState, pluggedType, sourceApi)
fun BatteryDto.toEntity() = BatterySnapshotEntity(0, capturedAtUtc, batteryLevelPercent, chargingState, pluggedType, sourceApi)
fun DataCoverageEntity.toDto() = CoverageDto(metricType, intervalStartUtc, intervalEndUtc, status, source, checkedAtUtc, notes)
fun CoverageDto.toEntity() = DataCoverageEntity(0, metricType, intervalStartUtc, intervalEndUtc, status, source, checkedAtUtc, notes)
fun DeviceProfileEntity.toDto() = DeviceProfileDto(manufacturer, model, androidRelease, apiLevel, firstLifelogTrackingAtUtc, userPurchaseDate, purchaseDateSource, lastSuccessfulCollectionAtUtc)
fun DeviceProfileDto.toEntity() = DeviceProfileEntity(DeviceProfileEntity.SINGLETON_ID, manufacturer, model, androidRelease, apiLevel, firstLifelogTrackingAtUtc, userPurchaseDate, purchaseDateSource, lastSuccessfulCollectionAtUtc)
