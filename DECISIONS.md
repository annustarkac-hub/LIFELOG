# DECISIONS

Record every important product/technical decision.

| ID | Date | Decision | Rationale | Consequences | Owner |
|---|---|---|---|---|---|
| D-001 | 2026-11-27 | Treat repository as greenfield. | Only `README.md` (`# LIFELOG`) exists; no code to preserve. | All project structure will be created new; README will be extended, not replaced. | Agent 00 |
| D-002 | 2026-11-27 | Adopt handover pack ADR-001 (local-first) and ADR-002 (tracking limits) as accepted baseline. | Marked "Accepted" in the pack. | No backend, no Accessibility Service, no invented capabilities. | Product owner (pack) |
| D-003 | 2026-11-27 | Device/OEM behaviour found without a physical device will be labelled UNVERIFIED. | No ADB device or KVM in the agent's container; pack forbids claiming passes without evidence. | Gate 2 needs the owner's phone (or owner-run builds) for verified results. | Agent 00 |
| D-004 | 2026-11-27 | Pending owner decision: whether to commit the handover pack into the repo (e.g. `docs/handover/`). | Keeps specs versioned beside code and lets later gates cite them. | If declined, the pack stays external and must be re-supplied each session. | Product owner |
