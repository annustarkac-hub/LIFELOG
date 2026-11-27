# Project Guardrails

## Product identity
LIFELOG is an advanced personal Android device-usage analytics app for the device owner's own phone.

## Product promise
"Understand and preserve the phone-usage history that Android legitimately makes available, and keep recording supported usage from the point LIFELOG is enabled."

Do not market it as a complete forensic recorder or as guaranteed recovery from device purchase date.

## Explicitly out of scope for MVP
- Cloud sync or remote account
- Multi-device aggregation
- Parental monitoring or employee surveillance
- Message/call content collection
- Keystroke/touch capture
- Screen recording
- Accessibility-service surveillance
- Hidden/background covert tracking
- Guaranteed purchase-date detection
- Guaranteed historical recovery before Android-retained data
- Medical diagnosis or addiction diagnosis

## Required labels in UI
- Historical data (returned by Android)
- Recorded by LIFELOG (since tracking started)
- User-provided purchase date (unverified)
- Data unavailable / coverage gap
- Last successful collection time

## Data rules
- Store timestamps in UTC internally; render in device-local time.
- Preserve original event timestamp and source.
- Track coverage intervals and collection checkpoints.
- De-duplicate using stable event identity where possible; otherwise use documented composite keys.
- App usage durations must not be summed across overlapping apps and described as total screen-on time.
- Separate device screen-on duration from app foreground usage.
- Do not infer a screen-off event if it was not observed.
- A missing event is not evidence that an event did not happen.
