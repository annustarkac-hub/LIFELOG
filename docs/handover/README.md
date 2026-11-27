# LIFELOG — AI Coding Agent Handover Pack

Version: 1.0  
Product: Advanced Personal Device Intelligence & Lifetime Analytics  
Platform: Android only  
Primary language: Kotlin  
UI: Jetpack Compose  
Storage: Local-first; no cloud backend in MVP

## What this pack is

This is a product, architecture, implementation, testing, and handover specification for an AI coding agent or human Android development team. It is not a completed Android application and does not contain a compiled APK.

## Start here

1. Read `00_START_HERE/MASTER_AGENT_INSTRUCTIONS.md`.
2. Read `00_START_HERE/PROJECT_GUARDRAILS.md`.
3. Read `01_PRODUCT/PRODUCT_REQUIREMENTS.md`.
4. Read `02_ARCHITECTURE/SYSTEM_ARCHITECTURE.md`.
5. Execute the agents in `03_AGENT_WORKFLOWS/AGENT_SEQUENCE.md` one at a time.
6. Do not begin full implementation until the feasibility spike passes.

## Critical product truth

A normal third-party Android app cannot reliably recover the complete phone usage history from the date the device was purchased. Android usage history/event retention is limited and varies by OS version and OEM. The app must distinguish:
- historical records returned by Android,
- records captured after LIFELOG tracking begins,
- unavailable/missing periods,
- user-entered purchase date (unverified unless user supplies evidence).

Do not invent, interpolate, or present missing data as measured data.

## MVP outcome

An offline-first Android app that:
- obtains user-granted Usage Access,
- reads the usage data/events that Android currently makes available,
- records and reconciles supported events going forward,
- calculates app-wise and device-level usage without double counting,
- stores data locally,
- provides a premium analytics UI,
- supports local export/backup and restore,
- clearly communicates data coverage and limitations.

## Suggested environment

Use the current stable Android Studio, Android Gradle Plugin, Kotlin, Compose BOM, and AndroidX libraries at implementation time. Verify versions against official documentation before pinning them. Do not use guessed or obsolete dependency versions.

## Folder guide

- `01_PRODUCT`: product scope, requirements, user stories, screen specs
- `02_ARCHITECTURE`: architecture, data model, ADRs
- `03_AGENT_WORKFLOWS`: sequential agent prompts and gates
- `04_ANDROID_IMPLEMENTATION`: implementation rules and Android API notes
- `05_DATA_AND_PRIVACY`: data dictionary, privacy and security
- `06_TESTING_QA`: test plan and acceptance criteria
- `07_RELEASE_HANDOVER`: release checklist and final handover
- `08_TEMPLATES`: decision log, issue and progress templates
