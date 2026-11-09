# Changelog

## 1.11.0 — 2026-11-09

- `requirements` on `Tasks.Task`, `Tasks.QuoteResponse` and `Presence.Response` (`Tasks.Requirements`): each vehicle Relay looks for and the equipment a rider on it needs, for example a box on a motorcycle for food. See [the box a task needs](https://docs.sendrelay.com.ng/concepts/dispatch#the-box-a-task-needs). The records gain a last component.
- `presence().get(latitude, longitude, resolution, filter)` with a `Presence.Filter` (`Presence.Filter.of("FOOD_DELIVERY")`, or every field: `vehicleType`, `hasBox`, `hasRefrigeration`, `hasAirConditioning`, `estimatedWeight`, `estimatedSize`) counts only riders who could take that job. The three-argument `get` is unchanged.

## 1.10.0 — 2026-11-08

- `relay.events().list()` and `listAll()`: the event log, every event for your account and mode, kept for 30 days. `types` filters by event type.
- `relay.tasks().events(taskId)`: every event about one task, oldest first.
- `relay.claims()`: insurance claims on a task, for insured goods lost, stolen or damaged after collection. `get`, `createUpload` (a URL to PUT one photo or PDF to), `file`, `respond` and `withdraw`. Live keys only.
- `relay.webhooks().redeliver(webhookId, new Webhooks.RedeliverRequest(eventId))`: send a logged event to an endpoint again, with the same id.
- `updatedSince` on `Tasks.ListParams` (a new four-argument constructor; the three-argument one still works): tasks changed since a time, in `updatedAt` order, for reconciliation jobs.
- `revision()` and `currency()` on `Task` and `TaskListItem`. Task events carry `data.revision`: apply an event only if it is higher than the revision you stored.
- `tasks().update(...)` and `tasks().trackingLink(...)`. `Stage` gains `contact`, `deliverItems`, `completionCode`, `completionCodeLength`, `arrivedAt` and `waitSeconds` (`withContact`, `withDeliverItems`, `withCompletionCode`); `DispatchRequest.withReadyAt` and `withOnRiderCancel`; `CreateRequest.withRequirePhoto`. Every earlier constructor still works.

## 1.9.0 — 2026-11-06

- Insurance on tasks: `withInsured(false)` on `CreateRequest` and `QuoteRequest` turns it off for that task. It is on by default.
- `insuranceFee` and `insuredValue` on `Pricing` and `QuoteResponse`. The premium is now its own line: VAT applies to it, and fare premiums and the platform fee no longer do.

## 1.8.0 — 2026-11-03

- Keep looking for a rider and scheduled pickups ([dispatch strategies](https://docs.sendrelay.com.ng/tasks/keep-looking-and-scheduling)).
- `Tasks.DispatchRequest` (`immediate()`, `waitForRider(minutes)`, `scheduled(pickupAt, window)`) via `withDispatch` on `QuoteRequest` and `CreateRequest`; the existing constructors still work. `Task.dispatch()` returns the search window.
- `tasks().simulateAdvance(taskId)` (test keys) starts a booking's search now.
- `Tasks.Item` takes an optional `reference`, and `Tasks.Location` optional `countryCode` (ISO 3166-1 alpha-2) and `postalCode`; the existing constructors still work. They are returned on the task when sent, and not used in pricing yet.
- Statuses and webhook event types this version doesn't list arrive as their raw strings. Relay will add task statuses (for example for tasks that keep searching for a rider), so treat a status you don't recognise as still in progress.

## 1.7.0 — 2026-10-29

- First release, versioned with the other Relay server SDKs (they move together). Matches the shared SDK conformance suite (packages/sdks/conformance).
