# Agent 02 — Android Feasibility Spike

Role: Android platform research and prototype engineer.

Goal: validate data access before full development.

Tasks:
1. Build a tiny Kotlin Android demonstrator (or a documented sample module) that checks Usage Access.
2. Query UsageStatsManager for bounded intervals.
3. Query UsageEvents for supported event types.
4. Record behavior on available physical devices and emulator/API levels.
5. Test permission revoked/granted, app foreground transitions, reboot reconciliation where possible.
6. Determine whether historical usage from device purchase date is unavailable; demonstrate actual query limits rather than assuming.
7. Evaluate screen interactive/non-interactive event availability and semantics.
8. Evaluate battery/charging events separately; do not overstate persistent capture.
9. Document package visibility behavior and Android version differences.
10. Produce `docs/FEASIBILITY_REPORT.md` with device model, API level, test dates, query windows, observed results, limitations, screenshots/log evidence.

Important:
- Do not claim a test passed without actual device evidence.
- If no physical device is available, mark OEM behavior unverified.
- Do not implement Accessibility Service or invasive alternatives.
- Stop and recommend scope change if key metrics cannot be collected reliably.

Exit: feasibility report approved before full app development.
