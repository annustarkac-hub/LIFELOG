# LIFELOG

Offline-first Android app for long-term analytics of the phone-usage data that Android legitimately makes available,
recorded from the moment LIFELOG is enabled. Kotlin · Jetpack Compose · Room · WorkManager. No internet permission.

> LIFELOG cannot recover full history since purchase. Android keeps detailed events only for a limited time; older
> periods have at most Android's coarser summaries. All gaps are shown as gaps.

## Build
Requirements: JDK 17+ (21 tested), Android SDK with platform `android-37.1` and build-tools 36+.
```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew :app:testDebugUnitTest          # 48 JVM/Robolectric tests
./gradlew :app:assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease :app:bundleRelease
```
Release signing reads a git-ignored `keystore.properties` at the repo root:
```
storeFile=release-keys/lifelog-release.jks
storePassword=…
keyAlias=lifelog
keyPassword=…
```
Without it, the release APK is built unsigned. **Never commit keystores or passwords.**

If Maven Central rate-limits Robolectric's large jars (HTTP 429), pass
`-ProbolectricRepoUrl=https://maven-central.storage-download.googleapis.com/maven2`.

## Install on a phone
1. Copy `LIFELOG-1.0.0-release.apk` to the phone and open it (allow "install unknown apps" for your file manager).
2. Open LIFELOG, read the disclosure, tick "I understand", tap **Open Usage Access settings**, enable LIFELOG.
3. Return to the app. Optional: Settings › Diagnostics › Save report to measure what your phone retains.

## Documentation
`docs/` — PRODUCT_BACKLOG, OPEN_QUESTIONS, FEASIBILITY_REPORT, ARCHITECTURE_REVIEW (+ `adr/`), UI_DESIGN_SYSTEM,
TRACKING_LIMITATIONS, DATABASE_SCHEMA, SECURITY_PRIVACY_AUDIT, QA_REPORT, RELEASE_NOTES, PLAY_STORE_CHECKLIST,
PRIVACY_POLICY, FINAL_HANDOVER. Original specification: `docs/handover/`. Status: `PROJECT_STATUS.md`, decisions: `DECISIONS.md`.
