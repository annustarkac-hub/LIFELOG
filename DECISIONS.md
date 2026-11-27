# DECISIONS

Record every important product/technical decision.

| ID | Date | Decision | Rationale | Consequences | Owner |
|---|---|---|---|---|---|
| D-001 | 2026-11-27 | Treat repository as greenfield. | Only `README.md` (`# LIFELOG`) exists; no code to preserve. | All project structure will be created new; README will be extended, not replaced. | Agent 00 |
| D-002 | 2026-11-27 | Adopt handover pack ADR-001 (local-first) and ADR-002 (tracking limits) as accepted baseline. | Marked "Accepted" in the pack. | No backend, no Accessibility Service, no invented capabilities. | Product owner (pack) |
| D-003 | 2026-11-27 | Device/OEM behaviour found without a physical device will be labelled UNVERIFIED. | No ADB device or KVM in the agent's container; pack forbids claiming passes without evidence. | Gate 2 needs the owner's phone (or owner-run builds) for verified results. | Agent 00 |
| D-004 | 2026-11-27 | Pending owner decision: whether to commit the handover pack into the repo (e.g. `docs/handover/`). | Keeps specs versioned beside code and lets later gates cite them. | If declined, the pack stays external and must be re-supplied each session. | Product owner |
| D-005 | 2026-11-27 | Owner delegated all decisions ("don't ask permission"); proceed through all gates without stopping. D-004 resolved: pack committed under docs/handover. | Explicit owner instruction. | Decisions resolved conservatively; listed in docs/OPEN_QUESTIONS.md. | Product owner |
| D-006 | 2026-11-27 | minSdk 29, compileSdk 37.1, targetSdk 36 (ADR-007). | API 29 events; Compose AAR metadata; untested target-37 behaviour. | Android 9 and below unsupported. | Agent 03 |
| D-007 | 2026-11-27 | Read-time aggregation, no usage_daily table (ADR-003). | No stale aggregates; union prevents double counting; TZ-safe. | More CPU on large ranges. | Agent 03 |
| D-008 | 2026-11-27 | Manual DI, single module (ADR-004). | Small app; simpler build. | — | Agent 03 |
| D-009 | 2026-11-27 | Durations only from observed start+end; no inferred values; shutdown discards open intervals. | Guardrails; Android reference. | Slight undercount, never overcount. | Agent 06 |
| D-010 | 2026-11-27 | Password-based AES-GCM backup encryption, not Keystore-bound (ADR-006). | Backups must survive factory reset. | Forgotten password = unrecoverable backup. | Agent 07 |
| D-011 | 2026-11-27 | Feasibility spike shipped as in-app Diagnostics probe; device results UNVERIFIED. | No device or emulator in agent environment. | Owner must run it. | Agent 02 |
| D-012 | 2026-11-27 | Release key generated for owner; v3 signing; never committed. | Owner asked for a final APK. | Owner must store the key; it can be used as Play upload key. | Agent 11 |
| D-013 | 2026-11-27 | Keep WorkManager's ACCESS_NETWORK_STATE; remove FOREGROUND_SERVICE. | Removal of the former is untested on device; it grants no network access. | Documented in security audit. | Agent 09 |
