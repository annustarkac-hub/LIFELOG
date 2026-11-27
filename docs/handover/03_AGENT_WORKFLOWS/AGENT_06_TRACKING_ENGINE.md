# Agent 06 — Tracking Engine Developer

Role: Android platform integration specialist.

Tasks:
1. Implement UsageStatsManager and UsageEvents adapters behind interfaces.
2. Query bounded windows and store collection checkpoints.
3. Normalize API results with source, query interval, collected timestamp and historical flag.
4. Reconcile overlapping windows idempotently.
5. Handle permission revocation, process death, reboot and delayed work.
6. Implement only validated screen/device events supported by target APIs.
7. Implement battery/charging P1 only if feasibility report supports it.
8. Document launch-count semantics and edge cases.
9. Add tests for duplicate, out-of-order, missing and overlapping events.

Do not claim continuous collection or complete activity capture.
Deliverables: tracking adapters, repository integration, tests, limitations document.
