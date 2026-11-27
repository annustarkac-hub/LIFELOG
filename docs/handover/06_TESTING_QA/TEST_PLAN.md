# LIFELOG Test Plan

## Test environments
Test on:
- At least one emulator for each supported API band.
- At least 2–3 physical devices from different OEMs for release confidence.
- Record model, Android version, API level, patch level and battery settings.

## Functional tests
1. First launch and disclosure.
2. Usage Access granted.
3. Usage Access denied/revoked.
4. Historical query with empty and populated result.
5. App usage list.
6. App detail date filters.
7. Timeline filters.
8. Dashboard date range.
9. Local backup.
10. Restore valid backup.
11. Reject corrupt/unsupported backup.
12. Delete all data.

## Data integrity tests
- Repeated overlapping query windows do not double count.
- Duplicate event insertion is idempotent.
- Out-of-order events are handled.
- Missing end event does not create false exact duration.
- Timezone change recomputes local-day buckets safely.
- Daylight-saving boundary tests where applicable.
- Device clock change does not corrupt UTC event ordering.
- Historical and recorded totals remain distinguishable.

## Lifecycle tests
- Reboot.
- App update.
- Process death.
- Force-stop (document expected behavior).
- Doze.
- Battery saver.
- OEM battery optimization.
- Usage Access revoked and re-enabled.

## Performance tests
- Large history dataset.
- Dashboard load time.
- Database query latency.
- Backup/restore duration.
- Battery impact under realistic sampling/reconciliation.

## Release blockers
- Any fabricated or misleading usage data.
- Double counting in P0 paths.
- Data loss on normal app update.
- Restore corrupts database.
- Sensitive data leaks to logs/network.
- Permission state misrepresented.
- P0 crash or data corruption.
