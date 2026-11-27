# Tracking Engine: Semantics and Limitations (Gate 6)

## Sources
| Data | API | Stored as |
|---|---|---|
| App foreground intervals | `queryEvents` ACTIVITY_RESUMED → ACTIVITY_PAUSED/STOPPED | `usage_record` APP_FOREGROUND |
| Screen interactive intervals | `queryEvents` SCREEN_INTERACTIVE → SCREEN_NON_INTERACTIVE | `usage_record` DEVICE_SCREEN_ON |
| Lock/unlock, startup/shutdown markers | `queryEvents` KEYGUARD_*, DEVICE_* | `system_event` |
| Daily per-app totals (before event coverage) | `queryUsageStats(INTERVAL_DAILY)` totalTimeInForeground | API_APP_DAILY |
| Daily screen totals (before event coverage) | `queryEventStats(INTERVAL_DAILY)` SCREEN_INTERACTIVE totalTime | API_SCREEN_DAILY |
| Monthly/yearly summaries | `queryUsageStats`/`queryEventStats` MONTHLY/YEARLY | API_*_MONTHLY/YEARLY (display only) |
| Battery | sticky `ACTION_BATTERY_CHANGED` at each run | `battery_snapshot` (sample) |

## Rules
- Duration only from an observed start AND observed end. No inferred ends (`is_inferred` is always false).
- Open intervals at query end are carried forward via `checkpoint.open_since_utc`.
- Open intervals across DEVICE_SHUTDOWN/STARTUP are discarded (Android reference guidance).
- Double SCREEN_INTERACTIVE: the earlier one is discarded (end unknown).
- Duplicates, re-delivery and overlapping windows: idempotent upsert by dedupe key + union at read time.
- Out-of-order events: stable sort by timestamp before reconstruction.

## Foreground entries ("launch count")
Count of per-app foreground intervals that start > 2 s after that app's previous interval ended. This is **not** a
cold-start count; activity-to-activity transitions inside one app are not counted. Not available on summary-only days.

## Known limitations
- Event retention is OS/OEM dependent ("a few days" per docs). Gaps longer than retention are permanent data loss and are shown.
- Collection is inexact: WorkManager (~1 h) plus on app open. Doze/OEM managers/force-stop can delay it.
- A session still open at the last run is counted after it ends (next run).
- Screen events are not ambient display; "interactive" ≠ "display physically lit".
- Android daily bucket boundaries are attributed to the local date of the bucket midpoint.
- Values may differ from Digital Wellbeing (different algorithms; no parity promised).
- Time zone: past days are re-bucketed into the *current* zone (ADR-005).
- Package visibility: apps without launcher entries may show only the package name.
