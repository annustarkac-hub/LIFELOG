# Acceptance Criteria

A P0 feature is accepted only when it has implementation evidence and automated/manual test evidence.

## P0 acceptance
- User sees clear disclosure before granting Usage Access.
- Permission status accurately reflects current access.
- Historical data is queried only within supported Android availability.
- App-wise usage is sourced from Android and stored with provenance.
- Lifetime recorded usage is based only on valid non-overlapping records.
- Screen-on time is not calculated by summing app usage.
- Coverage gaps and last successful collection are visible.
- Permission revocation pauses collection without deleting prior data.
- App restart/update does not erase app-owned records.
- Backup can be exported and restored with validation.
- User can delete local data.
- Core app works offline.
- No prohibited permissions or data collection.

## Required evidence
- Build output
- Unit test results
- Instrumentation test results where applicable
- Device test matrix
- Backup/restore round-trip
- Screenshot/video of key flows
- Known limitations document
