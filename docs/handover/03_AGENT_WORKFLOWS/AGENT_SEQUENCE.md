# Sequential Agent Workflow

Run one agent at a time. The same coding agent may act in each role, but must complete and report each gate before moving on.

## Gate 0 — Repository Intake Agent
Prompt: `AGENT_00_REPOSITORY_INTAKE.md`
Output: repository inventory, environment report, initial PROJECT_STATUS.md.
Exit: build environment understood; no code changes beyond status/docs unless needed.

## Gate 1 — Product Manager Agent
Prompt: `AGENT_01_PRODUCT_MANAGER.md`
Output: clarified scope, user stories, MVP backlog, unresolved decisions.
Exit: P0/P1/P2 scope approved; no impossible feature left as a promise.

## Gate 2 — Android Feasibility Agent
Prompt: `AGENT_02_FEASIBILITY_SPIKE.md`
Output: small demonstrator querying UsageStats/UsageEvents and documenting results on physical devices/emulators.
Exit: report proves what data is available and what isn't. If device testing is unavailable, clearly mark unverified and do not claim pass.

## Gate 3 — System Architect Agent
Prompt: `AGENT_03_ARCHITECT.md`
Output: module structure, data flow, schema, API boundaries, ADRs.
Exit: architecture review complete; no unresolved data integrity design.

## Gate 4 — UI/UX Agent
Prompt: `AGENT_04_UI_UX.md`
Output: design system, navigation, screen states, Compose implementation plan or UI code after architecture approval.
Exit: all MVP screens specified, including loading/empty/error/permission-denied states.

## Gate 5 — Android Core Agent
Prompt: `AGENT_05_ANDROID_CORE.md`
Output: buildable Android project, DI, navigation shell, theme, permissions explanation.
Exit: clean build and baseline tests.

## Gate 6 — Tracking Agent
Prompt: `AGENT_06_TRACKING_ENGINE.md`
Output: platform adapters, collection, reconciliation, coverage and checkpoints.
Exit: unit/instrumentation tests demonstrate no double counting in test fixtures.

## Gate 7 — Database & Analytics Agent
Prompt: `AGENT_07_DATABASE_ANALYTICS.md`
Output: Room entities/DAOs/migrations, aggregation use cases, reports.
Exit: data round-trip, migration and calculation tests pass.

## Gate 8 — Feature Integration Agent
Prompt: `AGENT_08_INTEGRATION.md`
Output: connected screens, ViewModels, timeline, exports, backup/restore.
Exit: end-to-end MVP flows pass.

## Gate 9 — Security & Privacy Agent
Prompt: `AGENT_09_SECURITY_PRIVACY.md`
Output: permission audit, data-flow audit, backup security review, privacy copy.
Exit: no unauthorized data access/transmission; risks documented.

## Gate 10 — QA Agent
Prompt: `AGENT_10_QA.md`
Output: test report, bug list, regression results, device matrix.
Exit: P0 tests pass; known limitations documented.

## Gate 11 — Release Agent
Prompt: `AGENT_11_RELEASE.md`
Output: release build, signing instructions, Play Store checklist, handover package.
Exit: release artifact built and verified; signing keys remain owner-controlled.

## Rules
- Do not skip Gate 2.
- Do not parallelize agents that edit the same files.
- UI design exploration may run in parallel with feasibility only if it does not assume unverified capabilities.
- If any gate fails, fix it before proceeding.
