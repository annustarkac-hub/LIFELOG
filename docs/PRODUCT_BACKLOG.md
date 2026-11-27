# LIFELOG MVP Backlog (Gate 1 — Product Manager)

Source documents: `docs/handover/01_PRODUCT/*`, `docs/handover/06_TESTING_QA/ACCEPTANCE_CRITERIA.md`.
Every P0 item has a testable acceptance criterion (AC). "Unit" = JVM unit test, "Robo" = Robolectric test,
"Device" = manual check on a physical phone (owner-run; see FEASIBILITY_REPORT.md).

## P0 — MVP

| ID | Feature | Acceptance criterion | Verification |
|---|---|---|---|
| P0-01 | Onboarding & disclosure | First launch shows what is / is not collected before any Usage Access request; user must tap "I understand" before the Settings CTA is enabled. Wording never says "all phone activity". | Robo/UI + copy review |
| P0-02 | Usage Access status | Status reads AppOps `OPSTR_GET_USAGE_STATS` on every resume; CTA opens `Settings.ACTION_USAGE_ACCESS_SETTINGS` (falls back to app settings if no activity). | Unit (mode mapping) + Device |
| P0-03 | Device overview | Shows manufacturer, model, Android release/API, first LIFELOG tracking time, user purchase date labelled "User-provided (unverified)", tracking status, last successful collection. No inferred purchase date. | Code review + UI |
| P0-04 | Historical retrieval | Historical import queries only Android APIs; records stored with `is_historical=true`, source API and query window. Nothing generated when API returns empty. | Unit (mapper) + Robo (DAO) |
| P0-05 | Event ingestion & reconciliation | Repeated/overlapping collection windows produce identical DB state (idempotent upsert on dedupe key). | Unit + Robo |
| P0-06 | No double counting | Overlapping activities of the same app are unioned; device "any-app foreground" is a union across apps, never a sum; screen-on is never derived from app usage. | Unit |
| P0-07 | Unmatched events | A start without an observed end yields no duration record (coverage marked PARTIAL); open events across DEVICE_SHUTDOWN→STARTUP are discarded per Android docs. | Unit |
| P0-08 | Dashboard | Today / 7d / 30d / lifetime cards; screen-on and app-foreground shown as separately labelled metrics; historical vs recorded split visible. | UI + Unit (aggregation) |
| P0-09 | Apps list/detail | Search, sort (usage/name); period usage + lifetime recorded; detail daily chart; uninstalled apps shown by package name. | UI + Unit |
| P0-10 | Timeline | Chronological supported events, filter by date, type, app; each event shows source API. | UI + Robo (query) |
| P0-11 | Coverage indicators | Per day coverage status (AVAILABLE/PARTIAL/UNAVAILABLE/NOT_GRANTED/UNKNOWN) and last successful collection visible on Home & Reports. | Unit (coverage calc) + UI |
| P0-12 | Local persistence | Room DB with exported schema JSON, no destructive migration fallback. | Build check (schema file) |
| P0-13 | JSON backup | Versioned JSON, SHA-256 checksum, optional password encryption (AES-256-GCM, PBKDF2). Written via SAF. | Unit round-trip |
| P0-14 | Validated restore | Rejects wrong format version, checksum mismatch, wrong password, oversize file, unknown record types; user chooses Merge or Replace explicitly. | Unit + Robo |
| P0-15 | CSV export | Daily per-app summary CSV via SAF, with provenance column. | Unit |
| P0-16 | Delete all data | Removes all DB rows, preferences and cache files; confirmation dialog. | Robo |
| P0-17 | Privacy & permissions screen | Usage Access status, tracking on/off, last collection, storage explanation, export, delete, limitations. | UI |
| P0-18 | Dark premium theme | Dark theme default, Material 3, contrast ≥ 4.5:1 for body text. | Design review |
| P0-19 | Permission revoked | Collection pauses, coverage NOT_GRANTED for the gap, no rows deleted. | Unit (collector) |
| P0-20 | Offline | No INTERNET permission in merged manifest. | Manifest check (build) |

## P1 — included where feasible
| ID | Feature | Decision |
|---|---|---|
| P1-01 | Battery snapshots | INCLUDED as *samples* taken when collection runs (sticky `ACTION_BATTERY_CHANGED`); labelled "sample", gaps shown. |
| P1-02 | Reboot markers | INCLUDED from `DEVICE_STARTUP`/`DEVICE_SHUTDOWN` usage events (API 29+). |
| P1-03 | Usage heatmap | INCLUDED (hour-of-day × weekday, recorded sessions only). |
| P1-04 | Yearly comparison | INCLUDED via Android-retained yearly/monthly buckets shown separately ("Android summaries"). |
| P1-05 | App categories | DEFERRED — `ApplicationInfo.category` is developer-declared and often UNDEFINED; not defensible enough. |
| P1-06 | Reminders | DEFERRED — would require POST_NOTIFICATIONS; not needed for MVP. |

## P2 — post-MVP (not built)
Advanced insights, PDF report, extra visualisations, widgets.

## Explicitly out of scope (cannot or must not be built)
Complete history since purchase; purchase-date detection; Accessibility-service monitoring; notification/message/keystroke/touch capture;
screen recording; cloud sync; multi-device; parental/employee monitoring; medical/addiction diagnosis; exact continuous background tracking.
