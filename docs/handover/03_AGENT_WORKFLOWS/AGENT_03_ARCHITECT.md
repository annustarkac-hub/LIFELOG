# Agent 03 — System Architect

Role: Senior Android architect.

Tasks:
1. Design modular package structure and dependency direction.
2. Define domain models, repository interfaces, platform adapters.
3. Review proposed data model and refine it.
4. Specify timestamp/time-zone handling, interval semantics, deduplication, reconciliation.
5. Define data coverage and provenance model.
6. Define WorkManager use and explicitly avoid exact/continuous scheduling assumptions.
7. Create ADRs for important decisions.
8. Produce architecture diagram and `docs/ARCHITECTURE_REVIEW.md`.

No feature implementation until architecture is reviewed.
Exit: architecture supports testability, local-first data, migrations and no double counting.
