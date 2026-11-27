# Implementation Standards

## Kotlin
- Prefer immutable data classes and sealed result/state types.
- Use coroutines/Flow for asynchronous data.
- Avoid blocking the main thread.
- Keep platform APIs behind interfaces.
- Handle cancellation and lifecycle correctly.

## Compose
- Stateless composables where possible.
- ViewModel exposes StateFlow UI state.
- Include previews with labelled fake data only.
- Accessibility semantics and scalable typography.
- Separate loading, empty, error, permission-denied and partial-coverage states.

## Architecture
- UI -> ViewModel -> UseCase -> Repository -> DataSource.
- No direct database or Android API calls from composables.
- Inject dependencies consistently.
- Avoid unnecessary abstraction and overengineering.

## Testing
- Unit tests for date math, dedupe, aggregation and coverage.
- Instrumentation tests for Usage Access/API adapters where feasible.
- Repository tests using test database.
- UI tests for permission and empty/error states.

## Code quality
- Formatting/lint checks.
- No hardcoded mock usage in production.
- No secrets or signing keys in repository.
- Document all API limitations.
