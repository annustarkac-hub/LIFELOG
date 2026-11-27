package com.lifelog.app.ui

import com.lifelog.app.data.TimeFormat
import com.lifelog.app.domain.CoverageStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Fmt {
    /** "3h 12m", "14m", "<1m". Minute precision only: no misleading seconds on aggregate figures. */
    fun duration(ms: Long?): String {
        if (ms == null) return "—"
        val totalMin = ms / 60_000
        if (ms in 1..59_999) return "<1m"
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h >= 100 -> "${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun time(epoch: Long, format: TimeFormat, zone: ZoneId = ZoneId.systemDefault()): String {
        val pattern = when (format) {
            TimeFormat.H24 -> DateTimeFormatter.ofPattern("HH:mm")
            TimeFormat.H12 -> DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            TimeFormat.SYSTEM -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        }
        return Instant.ofEpochMilli(epoch).atZone(zone).format(pattern)
    }

    fun dateTime(epoch: Long?, format: TimeFormat, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epoch == null) return "Never"
        return date(Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()) + ", " + time(epoch, format, zone)
    }

    fun date(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    fun weekday(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))

    fun relative(epoch: Long?, now: Long): String {
        if (epoch == null) return "never"
        val diff = (now - epoch).coerceAtLeast(0)
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            else -> "${diff / 86_400_000} d ago"
        }
    }

    fun coverage(c: CoverageStatus): String = when (c) {
        CoverageStatus.AVAILABLE -> "Available"
        CoverageStatus.PARTIAL -> "Partial"
        CoverageStatus.UNAVAILABLE -> "Data unavailable"
        CoverageStatus.NOT_GRANTED -> "Access not granted"
        CoverageStatus.UNKNOWN -> "Unknown"
    }
}
