# Screen-by-Screen Specification

## Navigation
Bottom navigation:
1. Home
2. Apps
3. Timeline
4. Reports
5. Vault

Settings and permissions reachable from profile/settings action.

## 1. Onboarding
- Explain supported data categories and limitations.
- Explain Usage Access in plain language.
- No deceptive wording such as "all phone activity".
- User accepts privacy disclosure.
- CTA opens Usage Access settings.
- On return, refresh permission status.

## 2. Device Overview
Display only available and appropriate metadata:
- Manufacturer/model
- Android version/API level
- LIFELOG first successful tracking timestamp
- User-entered purchase date (clearly marked unverified)
- Tracking status
- Last successful collection
Do not claim this is verified purchase/first-use date.

## 3. Home Dashboard
Cards:
- Screen-on time today
- App foreground usage today
- Last 7 days
- Last 30 days
- Total recorded since tracking began
- Historical data available
- Coverage status
- Most-used apps
Use separate labels for screen-on time and app foreground time.

## 4. Apps
- Search and sort app list.
- Show app name/icon where package visibility permits.
- Show selected-period usage and lifetime recorded usage.
- Detail screen: daily/weekly/monthly chart, available history, launch count only if robustly derived.
- Handle uninstalled apps as historical entries with package name and fallback label.

## 5. Timeline
- Chronological list of supported events.
- Filters: date range, event type, app.
- Event types only when supported: app foreground/background transitions, screen interactive/non-interactive events if available, reboot marker, battery/charging state where implemented.
- Show source and confidence/availability context in detail.
- Do not imply missing events did not occur.

## 6. Reports
- Day, week, month, year, custom range.
- Usage trend chart.
- App distribution.
- Compare periods.
- Export summary.
- Empty state when data unavailable.

## 7. Battery (P1)
- Battery level snapshots with timestamp.
- Charging state changes when observed.
- Show sampling frequency and gaps.
- Never promise true battery health or charge-cycle count unless supported by a documented API on the device.

## 8. Vault
- Create encrypted or user-protected local backup.
- Restore via Android Storage Access Framework.
- CSV export for human-readable summary.
- JSON backup for full structured data.
- Validate version, checksum, schema, and records before restore.
- Offer merge or replace semantics explicitly; never silently overwrite.

## 9. Privacy & Permissions
- Usage Access status
- Tracking on/off
- Last collection
- Data storage explanation
- Export data
- Delete all data
- Privacy policy
- Explain limitations and supported sources.

## 10. Settings
- Theme
- Week start preference
- Time format
- Data retention option if implemented
- App exclusion list (exclude from summaries; does not change OS permission)
- Notification preference if implemented
