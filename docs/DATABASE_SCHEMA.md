# Database Schema (Gate 7)

Room database `lifelog.db`, **version 1**. Machine-readable schema: `app/schemas/com.lifelog.app.data.db.LifelogDatabase/1.json`
(exported at build time; commit every new version).

| Table | Key | Purpose |
|---|---|---|
| app_identity | package_name | label, first/last seen, installed/visible flag |
| usage_record | id; unique dedupe_key; idx (package_name,start), (metric_type,start) | normalised intervals and Android buckets with provenance |
| system_event | id; unique dedupe_key; idx (event_time), (event_type,event_time) | screen/keyguard/startup/shutdown markers |
| battery_snapshot | id; unique captured_at_utc | battery samples |
| data_coverage | id; idx (metric_type, interval_start) | EVENTS / NOT_GRANTED coverage intervals |
| device_profile | id = 1 | model, Android version, first tracking, user purchase date (USER_ENTERED), last collection |
| collection_checkpoint | collector_name | last query end, open-since, status, error, summary refresh time |
| backup_metadata | id | export/restore history (no payload) |

`usage_daily` from DATA_MODEL.md is intentionally not stored (ADR-003): daily values are computed on read.

## Migration policy
- No `fallbackToDestructiveMigration`. Every schema bump needs a `Migration` plus a `MigrationTestHelper` test using the
  exported JSON (`room-testing` is already a test dependency).
- Backup format carries `schemaVersion`; newer-than-supported backups are rejected.

## Retention
Kept until the user deletes data. Raw Android events are not persisted.
