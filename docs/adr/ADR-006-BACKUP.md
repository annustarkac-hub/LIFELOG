# ADR-006: Backup format and encryption

Status: Accepted (2026-11-27)

## Decision
JSON envelope `lifelog-backup` v1: header (format/schema/app version, created time, record counts, SHA-256 of the
plaintext payload) plus either a plaintext payload or AES-256-GCM ciphertext keyed by PBKDF2-HMAC-SHA256
(210,000 iterations, 16-byte random salt, 12-byte random IV). Written/read only via Storage Access Framework.

## Rationale
A Keystore-bound key would make a backup unrestorable after a factory reset, which defeats its purpose. A user password
keeps the backup portable while authenticated encryption detects tampering. Unencrypted export remains available,
clearly labelled.

## Validation before restore
Size ≤ 256 MB, format id, version ≤ supported, GCM tag, checksum, schema decode with unknown keys rejected, every
enum/interval validated, header counts match. Apply in one transaction; Merge (existing wins) or Replace (explicit
second confirmation).
