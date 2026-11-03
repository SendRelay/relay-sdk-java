# Changelog

## 1.8.0 — 2026-11-03

- Keep looking for a rider and scheduled pickups ([dispatch strategies](https://docs.sendrelay.com.ng/tasks/keep-looking-and-scheduling)).
- `Tasks.DispatchRequest` (`immediate()`, `waitForRider(minutes)`, `scheduled(pickupAt, window)`) via `withDispatch` on `QuoteRequest` and `CreateRequest`; the existing constructors still work. `Task.dispatch()` returns the search window.
- `tasks().simulateAdvance(taskId)` (test keys) starts a booking's search now.
- `Tasks.Item` takes an optional `reference`, and `Tasks.Location` optional `countryCode` (ISO 3166-1 alpha-2) and `postalCode`; the existing constructors still work. They are returned on the task when sent, and not used in pricing yet.
- Statuses and webhook event types this version doesn't list arrive as their raw strings. Relay will add task statuses (for example for tasks that keep searching for a rider), so treat a status you don't recognise as still in progress.

## 1.7.0 — 2026-10-29

- First release, versioned with the other Relay server SDKs (they move together). Matches the shared SDK conformance suite (packages/sdks/conformance).
