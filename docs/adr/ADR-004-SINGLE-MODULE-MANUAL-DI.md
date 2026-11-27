# ADR-004: Single Gradle module with manual dependency injection

Status: Accepted (2026-11-27)

## Decision
One `:app` module with strict package layering; dependencies wired in `AppContainer`. Hilt/Dagger not used.

## Rationale
The app has ~10 injectable collaborators. Manual DI avoids annotation processing for DI, keeps builds simpler, and
keeps the platform boundary explicit (interfaces in `platform/Platform.kt`). Modules can be split later if the code grows.
