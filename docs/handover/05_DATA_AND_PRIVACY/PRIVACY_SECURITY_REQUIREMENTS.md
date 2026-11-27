# Privacy and Security Requirements

## Principles
- User's own device only.
- Local-first; no remote transmission in MVP.
- Transparent consent and permission rationale.
- Minimum permissions.
- User controls export, restore, stop tracking and deletion.

## Prohibited in MVP
- Message or notification content collection
- Password/keystroke capture
- Touchscreen surveillance
- Screen recording
- Accessibility Service used to inspect other apps
- Covert tracking or hidden operation
- Remote monitoring
- Uploading usage history

## Security controls
- Use Android Keystore-backed encryption for sensitive backup encryption keys where practical.
- Do not hardcode cryptographic keys.
- Use authenticated encryption for encrypted backup format.
- Validate backup size, schema, checksum/authentication tag and record types before import.
- Avoid logging package-level usage details in production logs.
- Ensure exported components are not unnecessarily exposed.
- Use SAF rather than broad storage permissions.
- Confirm deletion removes app-owned DB records and temporary files.
- Explain that app-private data can be lost on uninstall/device reset unless user exports a backup.

## Privacy UX
Explain:
- what is collected,
- why Usage Access is required,
- what cannot be collected,
- where data is stored,
- when tracking is paused,
- how to export/delete data.
