# ADR-002: Android tracking limitations

Status: Accepted

## Context
Android limits what ordinary apps can observe. Usage Access exposes specific usage statistics/events, not a universal activity stream.

## Decision
Use public Android APIs and clearly scope each metric. Do not use Accessibility Service, notification scraping, message access, screen capture, or covert monitoring in MVP.

## Consequences
- No promise of complete phone activity.
- No reliable purchase-date auto-detection.
- Historical usage limited by Android data availability and OEM behavior.
- Tracking gaps must be visible.
- Feature requests requiring privileged access must be rejected or escalated for product review.
