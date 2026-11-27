# Android API Guidance (Verify at implementation time)

This document describes intended API areas, not a guarantee of uniform OEM behavior. Confirm current behavior against official Android documentation and the feasibility spike.

## Usage access
- `UsageStatsManager` provides usage statistics subject to user-granted Usage Access and platform retention/availability.
- `queryUsageStats` returns aggregated stats for an interval; interval semantics and data availability must be tested.
- `queryEvents` returns available usage events for a time range; do not assume indefinite history.
- Usage access is granted in system Settings, not as a standard runtime permission dialog.
- Handle null/empty results and revoked access.

## Package visibility
- Android package visibility restrictions apply.
- Declare only justified `<queries>` entries or use permitted package visibility approaches.
- Do not request broad package visibility without a valid need and policy review.
- Installed app labels/icons may be unavailable for some packages.

## Background execution
- WorkManager is deferrable and inexact.
- Doze, app standby, OEM battery managers and force-stop can delay or stop work.
- A boot receiver does not guarantee uninterrupted capture.
- Reconcile from checkpoints when app runs again.
- Foreground services require valid use cases, user-visible notification and current platform/policy compliance; do not add one just to claim continuous tracking.

## Screen events
- Verify which screen-related event types are available through UsageEvents on target API levels.
- Do not treat app foreground events as a complete screen-on signal.
- If a screen-on metric cannot be reliably reconstructed, label it as Android-reported or unavailable rather than inventing it.

## Battery
- BatteryManager and documented system broadcasts may provide current battery state and some change events.
- Periodic snapshots are samples, not a continuous battery history.
- Battery health and charge-cycle count are not uniformly available to ordinary apps.

## Device date
- Standard APIs do not provide a verified retail purchase date.
- A user-entered date is user-provided and unverified.
- Manufacturing/build metadata is not purchase date.

## Official documentation to consult
- Android UsageStatsManager API reference
- Android UsageEvents API reference
- Android package visibility documentation
- Android background work / WorkManager documentation
- Android battery and power management documentation
- Android Storage Access Framework documentation
- Google Play User Data and Permissions policies

The coding agent must consult current official docs and record links/versions in the feasibility report.
