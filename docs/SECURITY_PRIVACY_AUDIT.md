# Security & Privacy Audit (Gate 9)

Date: 2026-11-27 · Scope: source, merged release manifest, dependency graph, release APK
(`aapt2 dump`, `apksigner verify`). This is a technical review, **not legal or Google Play approval**.

## 1. Permissions in the merged release manifest
| Permission | Origin | Justification / verdict |
|---|---|---|
| PACKAGE_USAGE_STATS | app | Core feature; special access granted by the user in Settings. ✅ |
| RECEIVE_BOOT_COMPLETED | WorkManager | Re-schedules periodic work after reboot. ✅ |
| WAKE_LOCK | WorkManager | Keeps CPU awake while a job runs. ✅ |
| ACCESS_NETWORK_STATE | WorkManager | Only needed for network constraints (unused). Normal permission; **does not allow network access**. Kept to avoid untested runtime risk. ⚠️ low |
| DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX core | Signature-level, app-internal. ✅ |
| FOREGROUND_SERVICE | WorkManager | **Removed** via `tools:node="remove"`, along with `SystemForegroundService`. ✅ |
| **INTERNET** | — | **Absent.** The app cannot transmit data. ✅ |

Confirmed absent: Accessibility Service, notification listener, SMS, call log, contacts, location, camera, microphone,
storage permissions, QUERY_ALL_PACKAGES.

## 2. Exported components
| Component | Exported | Protection |
|---|---|---|
| MainActivity | yes | launcher (required) |
| WorkManager SystemJobService | yes | `android.permission.BIND_JOB_SERVICE` (system only) |
| WorkManager DiagnosticsReceiver | yes | `android.permission.DUMP` (system/adb only) |
| ProfileInstallReceiver | yes | `android.permission.DUMP` |
| All others | no | — |

## 3. Dependencies
Release runtime classpath contains only AndroidX, JetBrains/kotlinx, Okio (used by DataStore for local file I/O) and
Guava's `listenablefuture` stub. **No analytics, crash reporting, advertising, or networking SDKs.**

## 4. Data at rest
- Room DB and DataStore live in app-private storage. `allowBackup=false`, `fullBackupContent=false`, and
  `dataExtractionRules` exclude every domain from cloud backup and device transfer.
- The DB is not encrypted at rest (it relies on Android file-based encryption and the app sandbox). ⚠️ Accepted for MVP; SQLCipher could be added later.

## 5. Backups (SAF)
- Created/opened only through Storage Access Framework (`CreateDocument`/`OpenDocument`); no file paths, no FileProvider, so no path traversal surface.
- Optional AES-256-GCM encryption with PBKDF2-HMAC-SHA256 (210k iterations, random salt/IV); the password is never stored.
- Unencrypted export is allowed but labelled. The test `encryptedRoundTripAndNoPlaintextLeak` asserts package names don't appear in encrypted files.
- Restore validation: size cap 256 MB (streamed check), format/version, GCM tag, SHA-256, strict schema (unknown keys rejected), enum/interval validation, header count match, single-transaction apply.
- CSV export neutralises formula injection (`=`,`+`,`-`,`@` prefixes) and RFC-4180-quotes fields.

## 6. Deletion
`AppContainer.deleteAllLocalData()` deletes all rows in every table (one transaction), clears DataStore, and empties
`cacheDir` (plus any `tmp_*` files). Verified by `CollectorIntegrationTest.deleteAllRemovesEveryTable`. Android's own
usage history is not affected (it isn't LIFELOG's to delete), and the UI says so.

## 7. Logging
The app code contains no `Log.*` calls. WorkManager logging is set to ERROR. No usage data is written to logs.

## 8. Signing
v3 signature, RSA-4096, generated for the owner; keystore and passwords are git-ignored (`*.jks`, `keystore.properties`, `/release-keys/`). Verified with `git check-ignore`.

## 9. Play policy items for owner review (not decided here)
1. **PACKAGE_USAGE_STATS** — Play may require a declaration/justification for usage-access apps; the core feature depends on it.
2. **Data safety form** — answer: data collected = app activity (app interactions); not shared; not transmitted off device; user can delete; optional encrypted export.
3. **Privacy policy URL** — Play requires a hosted URL; text draft is in-app (Privacy screen) and in `docs/PRIVACY_POLICY.md`.
4. Application ID `com.lifelog.app` must be confirmed (cannot be changed after publishing).

## Remediation checklist
- [x] Remove FOREGROUND_SERVICE and unused service
- [x] Confirm no INTERNET / no SDK telemetry
- [x] Exclude data from cloud backup
- [x] Validate restore inputs
- [ ] Owner: Play declarations, hosted privacy policy
- [ ] Optional: DB encryption at rest
