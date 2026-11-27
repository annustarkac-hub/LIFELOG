# ADR-003: Aggregate at read time instead of storing usage_daily

Status: Accepted (2026-11-27)

## Context
DATA_MODEL.md proposes a `usage_daily` table but warns against "redundant aggregates without a recomputation strategy".
Local-day attribution depends on the current time zone, and overlapping records must be unioned.

## Decision
Do not persist daily aggregates. `Analytics.days()` computes per-day values from normalised UTC records on every read.

## Consequences
- Time-zone changes and late-arriving records are reflected automatically; nothing can go stale.
- No double counting: unions are computed over all records for a day.
- Cost: lifetime views read all intervals. At ~100–300 sessions/day this is ~100k rows/year, acceptable for on-device
  computation off the main thread. If profiling on real devices shows otherwise, a cache table keyed by
  (local_date, zone) with invalidation on writes can be added without changing semantics.
