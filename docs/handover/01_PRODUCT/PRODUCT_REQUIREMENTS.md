# Product Requirements Document (PRD)

## 1. Summary
LIFELOG is an offline-first Android app for advanced users who want long-term analytics of supported phone and app usage activity on their own device.

## 2. Target platform and stack
- Android only
- Kotlin
- Jetpack Compose
- MVVM or a clearly documented unidirectional state architecture
- Room for local structured storage
- DataStore for preferences
- WorkManager for deferrable reconciliation/maintenance, not exact continuous tracking
- Android public APIs for usage and system information

## 3. Primary user goals
1. See total recorded device screen-on time for available periods.
2. See usage per installed/known app.
3. Review daily, weekly, monthly, yearly, and lifetime summaries.
4. Review supported system/app events in a timeline.
5. Understand what historical data is available and what is missing.
6. Preserve data locally and export/restore it.
7. Control tracking, permissions, and deletion.

## 4. Core features

### P0 — MVP required
- Onboarding and clear disclosure
- Usage Access explanation and permission status
- Device overview (non-sensitive, supported metadata only)
- Dashboard with today, 7-day, 30-day, and lifetime views
- App-wise usage list and detail
- Historical usage retrieval from Android's available query window
- Usage event ingestion and reconciliation
- Screen/device activity timeline for supported events
- Coverage and missing-data indicators
- Local Room persistence
- Deduplication and aggregation
- Local JSON backup/export and validated restore
- CSV export for summary data
- Data deletion controls
- Privacy and permission screen
- Premium dark theme; light theme optional if time allows

### P1 — include if feasibility and schedule permit
- Battery level snapshots and charging state events
- Reboot event markers
- Usage heatmap
- Yearly comparison
- App category summaries (only if category mapping is defensible)
- Optional local reminder notifications

### P2 — post-MVP
- More advanced trend insights
- PDF report
- Additional visualizations
- Custom dashboard widgets

## 5. Non-functional requirements
- Offline operation for core features
- No account required
- No network permission unless a reviewed feature needs it
- Minimal battery impact
- Graceful handling of permission revocation
- Database migrations
- Accessible typography and contrast
- No misleading precision
- Clear last collection time and coverage status
- Robust restore validation

## 6. Data definitions
- Screen-on time: duration the device display is known to be interactive/on, only where a reliable supported source exists. Do not equate with sum of app usage.
- App foreground usage: duration attributed to app foreground events/UsageStats according to Android API semantics.
- App launch count: count only well-defined launch/foreground transitions; document limitations.
- Lifetime: sum of valid, non-overlapping, persisted records within known coverage, not a claim about device's entire life.
- Purchase date: optional user input, never auto-verified by ordinary Android app APIs.
- Tracking start: first date/time LIFELOG has permission and successfully records data.

## 7. User stories
- As a user, I can understand what LIFELOG can and cannot track before granting access.
- As a user, I can grant Usage Access and see whether it is active.
- As a user, I can see usage totals for a selected period.
- As a user, I can inspect one app's usage history.
- As a user, I can filter the timeline by date and event type.
- As a user, I can see historical-data coverage and gaps.
- As a user, I can export a backup and restore it.
- As a user, I can stop tracking and delete all local data.
- As a user, I can see when data was last collected.

## 8. Success criteria
- No fabricated historical usage.
- No double-counted usage in tested scenarios.
- All displayed totals trace to stored records and source.
- App remains useful without network access.
- Backup/restore round-trip passes tests.
- Permission state and collection gaps are visible.
