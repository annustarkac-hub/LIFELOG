# Feasibility Report (Gate 2 — Android Feasibility Spike)

Date: 2026-11-27 · Author: coding agent · Status: **DESK-VERIFIED; DEVICE BEHAVIOUR UNVERIFIED**

## 1. Test environment actually available
| Item | Value |
|---|---|
| Physical devices | **None.** The agent runs in a headless cloud container with no USB/ADB device. |
| Emulators | **None runnable.** No `/dev/kvm` (no hardware acceleration). |
| Consequence | No claim in this report is backed by device evidence. Every device-dependent row is marked **UNVERIFIED**. |

Per AGENT_02 rules ("If no physical device is available, mark OEM behavior unverified"), the spike was delivered as a
**demonstrator built into the app**: *Settings › Diagnostics › Feasibility probe*
(`app/src/main/java/com/lifelog/app/diagnostics/FeasibilityProbe.kt`). On the owner's phone it measures and reports:
Usage Access state, number/types of events returned for a 30-day request, the earliest event actually returned
(= observed event retention), bucket counts and earliest bucket for DAILY/WEEKLY/MONTHLY/YEARLY usage and event stats,
launcher-visible package count, label resolvability, and a battery sample. The report contains counts and dates only, and can be saved via SAF.

**Owner action to verify:** install the APK, grant Usage Access, open Settings › Diagnostics, tap *Save report*, and attach it to this document.

## 2. Official documentation consulted (fetched 2026-11-27)
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/reference/android/app/usage/UsageEvents.Event
- https://developer.android.com/reference/android/app/usage/UsageStats
- https://developer.android.com/reference/android/app/usage/EventStats
- https://developer.android.com/build/releases/gradle-plugin (AGP 9.5 compatibility table)
- https://developer.android.com/build/migrate-to-built-in-kotlin

## 3. Findings from the API reference (documented facts)
| # | Topic | Documented behaviour | Design consequence |
|---|---|---|---|
| F1 | Access model | "Most methods on this API require the permission android.permission.PACKAGE_USAGE_STATS … the user of the device still needs to grant permission through the Settings application. See Settings.ACTION_USAGE_ACCESS_SETTINGS." | Special access, checked via AppOps on every resume; onboarding deep-links to Settings with fallbacks. |
| F2 | Event retention | `queryEvents`: "Events are only kept by the system for a few days." | **Full history since purchase is impossible.** LIFELOG asks for 30 days and records whatever returns; coverage starts at the earliest event actually returned. |
| F3 | Locked user | `queryEvents` / `queryEventStats`: "Starting from Android R, if the user's device is not in an unlocked state … then null will be returned." | Null ≠ "no usage". Collector records status `ANDROID_RETURNED_NULL`, adds no coverage, UI shows a retry banner. |
| F4 | Aggregates | Usage data is "aggregated into time intervals: days, weeks, months, and years"; "A request for data in the middle of a time interval will include that interval." | Daily buckets used only for days without event coverage; buckets attributed by midpoint; monthly/yearly shown separately, never merged. Retention length per interval is **not documented** → measured by the probe. |
| F5 | Foreground events | `ACTIVITY_RESUMED` (1) / `ACTIVITY_PAUSED` (2) / `ACTIVITY_STOPPED` (23), API 29+, reported per activity with class name. | Sessions are reconstructed per (package, class) and unioned per package. minSdk = 29. |
| F6 | Screen events | `SCREEN_INTERACTIVE` (15) "turned on for full user interaction, not ambient display"; `SCREEN_NON_INTERACTIVE` (16), API 28+. `queryEventStats` aggregates SCREEN_INTERACTIVE/NON_INTERACTIVE/KEYGUARD_SHOWN/HIDDEN. | Screen-on = interactive intervals from these events (recorded) or EventStats totals (historical). Never from app usage. |
| F7 | Shutdown | `DEVICE_SHUTDOWN` (26): treat all started activities as stopped; `DEVICE_STARTUP` (27): "Any open events without matching close events between DEVICE_SHUTDOWN and DEVICE_STARTUP should be ignored because the closing time is unknown." | Open intervals are discarded at shutdown/startup; no synthetic end times. |
| F8 | New API | `queryAppUsageDuration` (added in 37.2), "Data can only be queried for the past 30 days", calendar-aligned local days. | Not used: requires API 37.2; noted for future work. |

## 4. Device-dependent questions (UNVERIFIED — to be measured with the probe)
| Question | Expected (literature/experience, not verified) | Status |
|---|---|---|
| Actual event retention on owner's phone | Typically several days; varies by OS/OEM | **UNVERIFIED** |
| Daily/monthly/yearly bucket retention | Commonly longer for coarser intervals | **UNVERIFIED** |
| SCREEN_INTERACTIVE present on OEM builds | Expected on API 28+ | **UNVERIFIED** |
| Reboot reconciliation | Periodic WorkManager work is rescheduled by WorkManager after reboot | **UNVERIFIED on device** |
| Force-stop | Background work stops until app is opened again (platform behaviour) | **UNVERIFIED on device** |
| OEM battery managers delaying work | Likely on some OEMs | **UNVERIFIED** |
| Parity with Digital Wellbeing | Not promised; algorithms differ | **NOT TESTED** |

## 5. Package visibility
Android 11+ limits package visibility. LIFELOG declares only a `<queries>` launcher-intent filter (no `QUERY_ALL_PACKAGES`).
Packages without a launcher activity may have no resolvable label; they are shown by package name and flagged
"not installed or not visible" (the app cannot distinguish the two).

## 6. Battery
`ACTION_BATTERY_CHANGED` is a sticky broadcast readable without registering a receiver. LIFELOG stores a **sample** each
time collection runs (not continuous). Battery health and cycle count are not used. Power connect/disconnect broadcasts
are not registered.

## 7. Verdict
Key P0 metrics (app foreground, screen-on, coverage) are obtainable through documented public APIs within Android's
retention limits. No privileged or invasive mechanism is required. Proceeding with implementation is justified.
**Device behaviour remains unverified until the owner runs the probe on a physical device.**
