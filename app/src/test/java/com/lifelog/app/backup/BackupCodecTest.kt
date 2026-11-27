package com.lifelog.app.backup

import com.lifelog.app.data.backup.BackupCodec
import com.lifelog.app.data.backup.BackupException
import com.lifelog.app.data.backup.BackupPayload
import com.lifelog.app.data.backup.CoverageDto
import com.lifelog.app.data.backup.CsvExporter
import com.lifelog.app.data.backup.CsvRow
import com.lifelog.app.data.backup.UsageRecordDto
import com.lifelog.app.platform.UsageAccessMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val rec = UsageRecordDto("com.a", "APP_FOREGROUND", 100, 200, 100, "USAGE_EVENTS", "UsageStatsManager.queryEvents", 0, 300, 300, false, false, "APP_FOREGROUND|com.a|100|200")
    private val payload = BackupPayload(usageRecords = listOf(rec), coverage = listOf(CoverageDto("EVENTS", 0, 300, "AVAILABLE", "x", 300, null)))

    private fun expect(reason: BackupException.Reason, block: () -> Unit) {
        try { block(); fail("expected $reason") } catch (e: BackupException) { assertEquals(reason, e.reason) }
    }

    @Test fun plainRoundTrip() {
        val text = BackupCodec.encode(payload, "1.0.0", 1000, null)
        val v = BackupCodec.decodeAndValidate(text, text.length.toLong(), null)
        assertEquals(payload, v.payload)
        assertEquals(BackupCodec.ENC_NONE, v.envelope.encryption)
    }

    @Test fun encryptedRoundTripAndNoPlaintextLeak() {
        val text = BackupCodec.encode(payload, "1.0.0", 1000, "correct horse".toCharArray())
        assertFalse("package name must not appear in ciphertext file", text.contains("com.a"))
        val v = BackupCodec.decodeAndValidate(text, text.length.toLong(), "correct horse".toCharArray())
        assertEquals(payload, v.payload)
    }

    @Test fun wrongPasswordRejected() {
        val text = BackupCodec.encode(payload, "1.0.0", 1000, "correct horse".toCharArray())
        expect(BackupException.Reason.WRONG_PASSWORD_OR_TAMPERED) { BackupCodec.decodeAndValidate(text, text.length.toLong(), "wrong password".toCharArray()) }
        expect(BackupException.Reason.PASSWORD_REQUIRED) { BackupCodec.decodeAndValidate(text, text.length.toLong(), null) }
    }

    @Test fun tamperedPayloadFailsChecksum() {
        val text = BackupCodec.encode(payload, "1.0.0", 1000, null).replace("\\\"durationMs\\\":100", "\\\"durationMs\\\":999")
        assertTrue(text.contains("999"))
        expect(BackupException.Reason.CHECKSUM_MISMATCH) { BackupCodec.decodeAndValidate(text, text.length.toLong(), null) }
    }

    @Test fun unsupportedVersionAndGarbageRejected() {
        val text = BackupCodec.encode(payload, "1.0.0", 1000, null).replace("\"formatVersion\":1", "\"formatVersion\":99")
        expect(BackupException.Reason.UNSUPPORTED_VERSION) { BackupCodec.decodeAndValidate(text, text.length.toLong(), null) }
        expect(BackupException.Reason.NOT_A_BACKUP) { BackupCodec.decodeAndValidate("{\"hello\":1}", 11, null) }
        expect(BackupException.Reason.NOT_A_BACKUP) { BackupCodec.decodeAndValidate("not json", 8, null) }
        expect(BackupException.Reason.TOO_LARGE) { BackupCodec.decodeAndValidate("{}", BackupCodec.MAX_BYTES + 1, null) }
    }

    @Test fun invalidRecordsRejected() {
        val bad = payload.copy(usageRecords = listOf(rec.copy(metricType = "INVENTED_METRIC")))
        val text = BackupCodec.encode(bad, "1.0.0", 1000, null)
        expect(BackupException.Reason.INVALID_RECORDS) { BackupCodec.decodeAndValidate(text, text.length.toLong(), null) }
        val neg = BackupCodec.encode(payload.copy(usageRecords = listOf(rec.copy(startAtUtc = 500))), "1.0.0", 1000, null)
        expect(BackupException.Reason.INVALID_RECORDS) { BackupCodec.decodeAndValidate(neg, neg.length.toLong(), null) }
    }

    @Test fun shortPasswordRefused() {
        try { BackupCodec.encode(payload, "1", 1, "short".toCharArray()); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun csvEscapingAndFormulaInjection() {
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("'=HYPERLINK(1)", CsvExporter.escape("=HYPERLINK(1)"))
        assertEquals("1:01:01", CsvExporter.hms(3_661_000))
        val line = CsvExporter.line(CsvRow("2026-11-27", "APP_FOREGROUND", "com.a", "App, Inc", 60_000, 2, "EVENTS", "AVAILABLE"))
        assertEquals("2026-11-27,APP_FOREGROUND,com.a,\"App, Inc\",60000,0:01:00,2,EVENTS,AVAILABLE", line)
    }

    @Test fun usageAccessModeMapping() {
        assertTrue(UsageAccessMode.isGranted(UsageAccessMode.MODE_ALLOWED, false))
        assertFalse(UsageAccessMode.isGranted(UsageAccessMode.MODE_IGNORED, true))
        assertFalse(UsageAccessMode.isGranted(UsageAccessMode.MODE_ERRORED, true))
        assertTrue(UsageAccessMode.isGranted(UsageAccessMode.MODE_DEFAULT, true))
        assertFalse(UsageAccessMode.isGranted(UsageAccessMode.MODE_DEFAULT, false))
    }
}
