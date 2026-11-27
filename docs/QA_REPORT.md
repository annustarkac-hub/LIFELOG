# QA Report (Gate 10)

Date: 2026-11-27 · Build: 1.0.0 (versionCode 1) · Environment: cloud container (JDK 21, Gradle 9.9.2, AGP 9.5.3, SDK 37.1)

## 1. Automated results
Command: `./gradlew :app:testDebugUnitTest` → **48 tests, 0 failures** (JUnit XML in `app/build/test-results/testDebugUnitTest/`).

| Suite | Tests | Covers |
|---|---|---|
| EventReconstructorTest | 11 | duplicates, out-of-order, missing end, close-without-open, multi-activity union, shutdown/startup discard, double SCREEN_INTERACTIVE, screen ≠ app, entries rule |
| IntervalMathTest | 7 | union, midnight split, DST 23 h/25 h days, zone re-bucketing, clipping |
| AnalyticsTest | 10 | no double counting (same app / split screen), screen not from apps, midnight split, bucket-vs-event precedence, historical/recorded split, coverage statuses, future days, exclusions, midpoint attribution |
| BackupCodecTest | 9 | plain/encrypted round trip, no plaintext leak, wrong password, tamper → checksum, unsupported version, garbage, oversize, invalid records, CSV escaping/injection, AppOps mode mapping |
| CollectorIntegrationTest (Robolectric + Room) | 8 | idempotent repeated collection, open session across runs, permission revoked → paused & preserved, tracking disabled, retention-bounded first run marked historical, daily buckets only before coverage, backup merge/replace round trip, delete-all |
| ScreenUiTest (Robolectric Compose) | 3 | onboarding disclosure + CTA gated + no "all phone activity" wording, Home separate metrics + no-access banner, Vault controls; screenshots in `docs/screenshots/` |

Static checks: Android lint **0 errors, 1 warning** (OldTargetApi — intentional, ADR-007). Release build with R8: success.

## 2. Test-plan coverage matrix
| TEST_PLAN item | Status | Evidence |
|---|---|---|
| First launch & disclosure | PASS (Robolectric) | ScreenUiTest |
| Usage Access granted / denied / revoked | PASS (logic, fakes) · **device UNVERIFIED** | CollectorIntegrationTest |
| Historical query empty/populated | PASS (fakes) · **device UNVERIFIED** | CollectorIntegrationTest |
| App list / detail / timeline / dashboard filters | Compiled; Home rendered; others **not UI-tested** | — |
| Local backup / restore valid / reject corrupt | PASS | BackupCodecTest, CollectorIntegrationTest |
| Delete all data | PASS | CollectorIntegrationTest |
| Overlapping windows / duplicates / out-of-order / missing end | PASS | EventReconstructorTest, CollectorIntegrationTest |
| Timezone / DST | PASS | IntervalMathTest |
| Device clock change | Design-level only (ADR-005) | — |
| Historical vs recorded distinguishable | PASS | AnalyticsTest, CollectorIntegrationTest |
| Reboot, app update, process death, force-stop, Doze, battery saver, OEM optimisation | **NOT TESTED — no device** | — |
| Performance with large history | **NOT TESTED** | — |
| Older backup schema | N/A (only v1 exists); newer rejected — PASS | BackupCodecTest |
| Insufficient storage | **NOT TESTED** | — |

## 3. Device matrix
| Manufacturer/model | Android/API | Result | Notes |
|---|---|---|---|
| — | — | **No device available** | Owner must run the APK + Diagnostics probe on at least one physical phone |

## 4. Fabrication check
No production code path generates usage values: all figures come from `usage_record` rows written by the Collector
from Android API results. Fixture data exists only under `app/src/test` and is labelled "(fixture)". `is_inferred` is never true.

## 5. Bugs found and fixed during QA
| ID | Severity | Issue | Fix |
|---|---|---|---|
| QA-1 | P1 | Summary-only days would have summed per-app totals into "any app" time (double-count risk) | Separate `summaryPerAppSumMs`, never in device totals |
| QA-2 | P2 | Wrong-password retry dialog lost state | Envelope carried into retry state |
| QA-3 | P1 | Collection could run after "Delete all" before onboarding re-accepted | Collector gated on onboarding acceptance |
| QA-4 | P2 | Text outside cards rendered black on dark background | Theme supplies default content colour |
| QA-5 | P3 | Uneven metric tile heights | Intrinsic-height rows |

## 6. Release recommendation
**Conditional GO for owner side-loading / internal testing.** Not recommended for public Play release until the
device-dependent rows above are executed on ≥ 2 physical phones from different OEMs, per TEST_PLAN.md.
