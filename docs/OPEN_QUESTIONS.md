# Open Questions & Resolutions (Gate 1)

The owner delegated all decisions to the agent on 2026-11-27 ("don't ask my permission"). Each question below was resolved
with the most conservative option consistent with the handover pack. Items marked **OWNER** should be revisited before Play publication.

| # | Question | Resolution |
|---|---|---|
| Q1 | What is "screen-on time"? | Android-reported interactive time: from `SCREEN_INTERACTIVE`→`SCREEN_NON_INTERACTIVE` event pairs (recorded) and `queryEventStats` SCREEN_INTERACTIVE totals (historical). Never from app usage. |
| Q2 | What is an "app launch"? | Count of foreground *entries*: an `ACTIVITY_RESUMED` for a package when no activity of that package was resumed in the previous 2 s gap. Shown as "foreground entries", not "launches". |
| Q3 | What is "lifetime"? | Sum of non-overlapping stored records since the earliest stored record. Always labelled "since <date>" and "recorded by LIFELOG" vs "historical from Android". |
| Q4 | Purchase date? | Optional user input, stored with source USER_ENTERED, always labelled unverified; never used in calculations. |
| Q5 | Minimum Android version? | API 29 (Android 10): required for `ACTIVITY_RESUMED/STOPPED`, `DEVICE_STARTUP/SHUTDOWN`. |
| Q6 | Application ID? | `com.lifelog.app` — **OWNER** to confirm before Play upload (cannot change after publishing). |
| Q7 | Backup encryption? | Optional password (AES-256-GCM, PBKDF2-HMAC-SHA256). Keystore-only keys rejected because a backup must be restorable after factory reset. |
| Q8 | Dependency injection? | Manual container (no Hilt) to reduce build complexity; documented in ADR-004. |
| Q9 | Background cadence? | WorkManager periodic ~1 h (inexact) + collection on app open. Events retained "a few days" by Android, so a gap > retention is a real data loss and is shown as a coverage gap. |
| Q10 | Package visibility? | `<queries>` for launcher intent only; no `QUERY_ALL_PACKAGES`. Non-visible apps show package name. |
| Q11 | Launcher/system UI in totals? | Included as recorded; user can exclude apps in Settings. |
| Q12 | Retention of raw events? | Raw usage events are not stored; only normalised sessions, screen intervals and system markers. Kept until user deletes. |
| Q13 | Signing key ownership | A release key is generated for the owner, delivered to them, never committed. **OWNER** must store it safely; losing it prevents updates. |
