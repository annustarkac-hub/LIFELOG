# Architecture Review (Gate 3 — System Architect)

## 1. Module and package structure
Single Gradle module `:app` (decision ADR-004), layered by package with one-way dependencies:

```
ui (Compose screens, ViewModels)  ──►  data (repository, analytics service, backup, settings)
        │                                   │
        └──────────────►  domain (models, engine: IntervalMath, EventReconstructor, Analytics)  ◄── collect (Collector, Worker)
                                            ▲
platform (UsageStatsManager / PackageManager / Battery / Device adapters behind interfaces) ──┘
```

| Package | Responsibility | Depends on |
|---|---|---|
| `domain` | Pure Kotlin models and algorithms. No Android imports. | — |
| `platform` | The only code calling Android system services (`AndroidPlatform.kt`), behind `UsageStatsSource`, `AppInfoSource`, `BatterySource`, `DeviceInfoSource`, `Clock`. | domain |
| `data` | Room DB, DAOs, `LifelogRepository` (transaction boundary), `AnalyticsService` (read side), `BackupManager`, `SettingsStore`. | domain |
| `collect` | `Collector` use case (collection pipeline) and `CollectionWorker`. | domain, data, platform interfaces |
| `ui` | ViewModels expose `StateFlow`; composables are stateless renderers. No DB/system calls from composables. | data, domain |
| `diagnostics` | Feasibility probe. | platform |

Dependency container: `AppContainer` (manual DI).

## 2. Data flow
```
          ┌───────────── on app resume / "Collect now" / WorkManager (~1 h, inexact) ─────────────┐
          ▼                                                                                        │
 Collector ─► hasUsageAccess? ──no──► coverage NOT_GRANTED, checkpoint status, keep all data       │
          │yes
          ▼
 queryEvents([min(lastEnd − 15 min, openSince), now)) ── null ──► status ANDROID_RETURNED_NULL
          ▼
 EventReconstructor (pure): sessions, screen intervals, markers, openSince
          ▼
 Room transaction: REPLACE by dedupe_key (idempotent) · coverage extend · checkpoint · profile · app identity
          ▼
 every 6 h: Android aggregates (daily before event coverage; monthly/yearly as separate summaries)
          ▼
 dataVersion++ ─► ViewModels ─► AnalyticsService ─► Analytics.days() (pure) ─► UI
```

## 3. Time model
- All stored timestamps are UTC epoch milliseconds, exactly as Android reports them.
- Local days are computed **at read time** with the device's *current* `ZoneId` (`IntervalMath.dayBounds`), so a
  time-zone change automatically re-buckets every day; no stored local-day aggregates can go stale (ADR-005).
- DST: day bounds come from `LocalDate.atStartOfDay(zone)`, giving 23 h / 25 h days correctly (unit-tested).
- Intervals are half-open `[start, end)`.
- Device clock changes: ordering uses Android's event timestamps; reconstruction sorts by timestamp; duplicates are
  idempotent by key. A backwards clock jump can create overlapping records; read-time unions prevent double counting.

## 4. De-duplication and reconciliation
| Record | Dedupe key | Conflict policy (collection / restore-merge) |
|---|---|---|
| App session | `APP_FOREGROUND|pkg|start|end` | REPLACE / IGNORE |
| Screen interval | `DEVICE_SCREEN_ON|-|start|end` | REPLACE / IGNORE |
| Android bucket | `API_*|pkg|bucketStart` | REPLACE (current buckets update) / IGNORE |
| System marker | `SE|type|ts` | REPLACE / IGNORE |
| Battery sample | unique `captured_at_utc` | IGNORE |

Two reconstructions of the "same" session from different windows can differ (e.g. a wider window reveals an earlier
activity). Both rows may exist; **all totals are computed from per-package interval unions**, and "any app" is the union
across packages. Double counting is therefore impossible by construction, not only by key.

## 5. Coverage and provenance
- `data_coverage` rows of kind `EVENTS` record intervals where events were successfully read. The first run, or any run
  after a gap > 24 h, claims coverage only from the earliest event Android actually returned (we cannot distinguish "no
  usage" from "not retained" before it). Contiguous runs extend the latest row.
- `NOT_GRANTED` rows record attempts without Usage Access.
- Per day: covered fraction ≥ 99.9 % → AVAILABLE; > 0 → PARTIAL; else Android daily buckets → AVAILABLE (summary
  source); else NOT_GRANTED / UNKNOWN (before any data) / UNAVAILABLE.
- Provenance per record: `source_type`, `source_api`, query window, `collected_at_utc`, `is_historical`
  (start < first tracking), `is_inferred` (always false; nothing is inferred).

## 6. Background work
`CollectionWorker`: unique periodic work (1 h, flex 20 min), policy KEEP. No exact alarms, no foreground service, no
boot receiver of our own (WorkManager re-schedules after reboot). Correctness never depends on timing: each run
reconciles from the checkpoint. Gaps longer than Android's retention are visible as coverage gaps.

## 7. Failure handling
| Failure | Behaviour |
|---|---|
| Permission revoked | Pause, NOT_GRANTED coverage, no deletion, banner. |
| Android returns null | No coverage claimed, status recorded, retry banner. |
| Process death mid-collection | Single Room transaction: all-or-nothing; next run re-queries from checkpoint. |
| Migration failure | No destructive fallback; app fails rather than wiping data. Export backup recommended before updates. |
| Restore failure | Validation before write; apply in one transaction (rollback on error). |

## 8. ADRs
- ADR-001 Local-first (pack, accepted)
- ADR-002 Tracking limits (pack, accepted)
- [ADR-003 Read-time aggregation](adr/ADR-003-READ-TIME-AGGREGATION.md)
- [ADR-004 Single module, manual DI](adr/ADR-004-SINGLE-MODULE-MANUAL-DI.md)
- [ADR-005 Time-zone handling](adr/ADR-005-TIMEZONE.md)
- [ADR-006 Backup format and encryption](adr/ADR-006-BACKUP.md)
- [ADR-007 minSdk 29](adr/ADR-007-MINSDK.md)

## 9. Review verdict
Supports testability (pure engine with JVM tests; adapters behind interfaces with fakes), local-first data, migrations
(exported schema, no destructive fallback) and no double counting (union-based totals). No unresolved data-integrity design.
