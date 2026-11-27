# Data Dictionary and Metric Semantics

## Device screen-on duration
Definition: duration for which a supported source reliably indicates display is on/interactive. Must not be derived by summing app durations. If only partial events are available, show partial/unknown coverage.

## App foreground duration
Definition: app foreground time as obtained from Android usage stats or reconstructed from documented events. Overlapping/missing events must be handled explicitly.

## App launch count
Definition: count of validated transitions that represent a launch/foreground entry under documented rules. Do not equate every usage event with a user launch.

## Historical usage
Data returned by Android for periods before LIFELOG first successful collection. Retention and completeness are not guaranteed.

## LIFELOG-recorded usage
Data collected and persisted by LIFELOG after permission is granted and tracking begins. Gaps may still occur due to OS/OEM restrictions, force-stop, permission revocation, or battery management.

## Lifetime recorded usage
Sum of valid non-overlapping records within known coverage. Not equivalent to complete device lifetime.

## Purchase date
Optional user-entered date. Source must be stored as USER_ENTERED. Do not label verified.

## Data coverage
For each metric and time interval, classify:
- AVAILABLE
- PARTIAL
- UNAVAILABLE
- NOT_GRANTED
- UNKNOWN

## Provenance fields
Every record should identify source API, event/metric type, original event time, collection time, query interval, historical/recorded status, and whether inferred.
