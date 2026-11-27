# ADR-001: Local-first storage

Status: Accepted

## Context
The user explicitly requires phone-only storage and no cloud backup in the MVP.

## Decision
Use Room as the local structured database and DataStore for preferences. Backups are user-initiated local files through Storage Access Framework. No remote backend.

## Consequences
- No account/authentication required.
- User controls backup location.
- App uninstall/factory reset can remove internal data; explain this clearly.
- Backup restore must be tested.
- Sensitive backups should be encrypted or user-protected.
