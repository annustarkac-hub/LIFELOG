# ADR-007: minSdk 29 (Android 10), compileSdk 37.1, targetSdk 36

Status: Accepted (2026-11-27)

## Decision
minSdk 29 because ACTIVITY_RESUMED/PAUSED/STOPPED and DEVICE_STARTUP/SHUTDOWN usage events exist from API 29.
compileSdk 37 (minor 1) because Compose BOM 2026.11.01 libraries require it (AAR metadata check).
targetSdk 36 because target-37 behaviour changes have not been reviewed or tested on a device. Raise it after testing.
