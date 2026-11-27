# ADR-005: UTC storage, current-zone rendering

Status: Accepted (2026-11-27)

## Decision
Store Android timestamps unchanged (UTC epoch ms). Compute local days with the device's current `ZoneId` at read time.

## Consequences
- After travelling, past days are shown in the current zone. This is a deliberate, documented simplification; the
  original zone of each event is not reported by UsageStatsManager.
- DST transitions are handled by `java.time` (23/25-hour days covered by tests).
