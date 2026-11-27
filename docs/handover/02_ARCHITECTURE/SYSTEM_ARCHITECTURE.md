# System Architecture

## Architecture principles
- Offline-first
- Modular, testable, source-aware data pipeline
- UI never directly queries Android system APIs
- Repository boundary between system APIs and domain logic
- Room as source of persisted app-owned records
- All aggregations reproducible from raw/normalized records where practical

## Layers

### Presentation
Compose screens, ViewModels, immutable UI state, navigation.

### Domain
Use cases:
- CheckUsageAccess
- CollectAvailableHistoricalUsage
- CollectUsageEvents
- ReconcileUsageWindow
- CalculateDailyUsage
- CalculateLifetimeUsage
- GetCoverageSummary
- ExportBackup
- RestoreBackup
- DeleteLocalData

### Data
Repositories, Room DAOs, mappers, backup codecs, preferences.

### Platform adapters
- UsageStatsManager adapter
- UsageEvents adapter
- PackageManager adapter subject to package visibility
- BatteryManager / supported broadcasts adapter
- Boot receiver only for permitted rescheduling/reconciliation
- Device metadata adapter

## Collection model
1. Check Usage Access.
2. Query Android for available stats/events over bounded windows.
3. Normalize source data.
4. Persist event/usage records with source, timestamp, query interval and collection time.
5. Reconcile overlapping query windows idempotently.
6. Update daily aggregates transactionally.
7. Update coverage intervals/checkpoint.
8. UI reads summaries and coverage from repository.

## Background work
WorkManager is for deferrable periodic reconciliation and maintenance. Do not promise exact execution times. If a feature requires continuous or exact monitoring, stop and evaluate feasibility, platform restrictions, foreground-service policy, user-visible notification requirements, and battery impact before implementing.

## Time model
- Persist timestamps as epoch milliseconds/Instant in UTC.
- Persist the original timezone/offset or zone context when useful for local-day attribution.
- Define daily buckets using device-local calendar boundaries.
- Recompute affected buckets after timezone changes.
- Use half-open intervals [start, end) for queries and duration math.

## Usage math
- Do not sum app foreground durations and label the result device screen-on time.
- Treat Android UsageStats totals as API-provided metrics with documented semantics.
- When reconstructing sessions from events, handle missing, duplicated, and out-of-order events.
- Cap/validate durations against query interval and known device state.
- Do not create synthetic session end times without marking them as inferred.
- Prefer Android aggregated usage stats for app totals where appropriate; use events for timeline/session detail.
- Document differences between APIs and test against Digital Wellbeing/system settings where possible, without promising exact parity across OEMs.

## Failure handling
- Permission revoked: pause collection, preserve data, show status.
- App force-stopped: no guarantee of immediate background recovery; reconcile on next launch.
- Device reboot: resume eligible work and reconcile from last checkpoint.
- Database migration failure: fail safely, preserve backup, never wipe silently.
- OEM restrictions: expose collection health and gaps.
