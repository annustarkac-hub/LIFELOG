# LIFELOG 1.0.0 (versionCode 1) — 2026-11-27

First MVP build.
- Onboarding with clear disclosure; Usage Access status and Settings deep link
- Collection from Android usage events with checkpoints, idempotent reconciliation and coverage tracking
- Import of Android daily/monthly/yearly summaries, kept separate from LIFELOG-recorded data
- Home dashboard (today, 7/30 days, recorded since tracking began, historical availability, coverage, top apps)
- Apps list/detail, Timeline with filters, Reports (day/week/month/year/custom, comparison, heatmap, Android summaries)
- Vault: JSON backup (optional AES-256-GCM password), validated restore with Merge/Replace, CSV export, delete all
- Privacy & permissions, Settings (theme, week start, time format, exclusions), Device overview (unverified purchase date), Diagnostics probe
- No internet permission, no third-party SDKs

Known limitations: see docs/TRACKING_LIMITATIONS.md and docs/QA_REPORT.md.
