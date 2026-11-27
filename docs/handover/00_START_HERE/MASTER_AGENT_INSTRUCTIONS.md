# MASTER INSTRUCTIONS FOR THE CODING AGENT

You are the engineering agent responsible for implementing LIFELOG. Work sequentially, inspect the repository before changing it, and keep the product constraints below intact.

## First actions

1. Inspect the existing repository and identify whether it is empty, an existing Android project, or a partially implemented project.
2. Summarize the current state before editing.
3. Read every specification in this pack.
4. Create `PROJECT_STATUS.md` and `DECISIONS.md` at repository root if absent.
5. Create a task checklist from `03_AGENT_WORKFLOWS/AGENT_SEQUENCE.md`.
6. Do not generate a large amount of code before validating the feasibility spike.

## Mandatory engineering rules

- Android native app; Kotlin + Jetpack Compose.
- Local-first MVP. No cloud, account system, analytics SDK, ads SDK, or remote telemetry unless explicitly approved.
- Use supported public Android APIs only.
- Request only permissions/access essential to a user-visible feature.
- Usage Access is special app access granted by the user in Settings; explain it and deep-link to the appropriate settings screen where supported.
- Never claim access to all phone activity.
- Never read private messages, passwords, keystrokes, notification contents, or content inside other apps.
- Do not implement unrestricted touch logging, covert surveillance, or Accessibility Service monitoring.
- Do not infer the actual purchase date from manufacture date, first boot, build fingerprint, app install date, or Android setup date.
- Historical usage is only what Android returns. Keep historical and LIFELOG-recorded data separately identifiable.
- Do not fabricate missing history, fill gaps with estimates, or label estimates as actual.
- Prevent double counting and preserve event provenance.
- Do not assume WorkManager can run continuously or at exact intervals.
- Account for Doze, OEM battery restrictions, process death, reboot, time-zone changes, daylight-saving changes, and app upgrades.
- Do not silently discard data when permissions are revoked.
- Avoid storing raw event streams indefinitely if summarized records are sufficient; document retention policy.
- Use transactions, migrations, indexes, and robust backup validation.
- Keep all secrets out of source control.
- Do not mark a task complete without build/test evidence.

## Required workflow

Follow the sequence in `03_AGENT_WORKFLOWS/AGENT_SEQUENCE.md`. Each agent must:
1. Read its prompt.
2. Inspect current project state.
3. Work only within its scope.
4. Run relevant tests/builds.
5. Update `PROJECT_STATUS.md` and `DECISIONS.md`.
6. Report changed files, commands run, results, known limitations, and next gate.

## Stop conditions

Stop and report rather than guessing if:
- a requested feature requires privileged/system-only access,
- Android API behavior is ambiguous,
- required data cannot be obtained on test devices,
- Play policy compliance is unclear,
- a migration could destroy user data,
- dependency versions cannot be verified,
- the project cannot build due to environment/tooling constraints.

## Definition of done

The app is not done merely because screens render. It must pass the acceptance criteria and QA plan, clearly show data coverage, preserve local records, and provide a tested backup/restore path.
