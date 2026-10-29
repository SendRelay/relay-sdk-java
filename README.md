# Relay Java SDK

The official server SDK for [Relay](https://sendrelay.com.ng), the delivery API, for Java and Kotlin. Quote and create deliveries, read them back with the rider's live ETA, manage webhooks, and mint tracking tokens for your apps.

```kotlin
// build.gradle.kts
implementation("ng.com.sendrelay:relay-java:1.7.0")
```

Java 17 or newer. Depends on Jackson. For apps (Android), use `ng.com.sendrelay:relay-android` instead.

## Quick start

```java
Relay relay = Relay.builder(System.getenv("RELAY_API_KEY")).build(); // sk_test_... or sk_live_..., server only

var stages = List.of(
    Tasks.Stage.pickup(new Tasks.Location(6.5244, 3.3792, "12 Marina Road, Lagos"), "Call on arrival",
        List.of(new Tasks.Item("Phone case", 500000, "LIGHT", "BOX"))),
    Tasks.Stage.dropoff(new Tasks.Location(6.4474, 3.4723, "22 Admiralty Way, Lekki"), "Leave at reception"));

Tasks.QuoteResponse quote = relay.tasks().quote(new Tasks.QuoteRequest("PACKAGE_DELIVERY", stages));
Tasks.CreateResponse created = relay.tasks().create(
    new Tasks.CreateRequest("PACKAGE_DELIVERY", stages).withQuoteId(quote.quoteId()),
    "order-1042"); // retries with the same key never create a second delivery

Tasks.GetResponse read = relay.tasks().get(created.task().id());
if (read.liveEta() != null) {
    System.out.println("Arriving at " + read.liveEta().arrivesAt());
}
```

Kotlin uses the same API: `relay.tasks().get(id).liveEta?.arrivesAt`.

Calls are blocking and the client is thread-safe; share one `Relay`. On Java 21, run calls on virtual threads for concurrency.

## Webhooks

```java
Webhooks.Verification result = Webhooks.verifySignature(rawBody, request.getHeader("X-Relay-Signature"), secret);
if (!result.valid()) return ResponseEntity.status(401).build();
```

Pass the raw request body exactly as received.

## Errors and retries

All errors are unchecked and extend `RelayException`:

- `ApiException` has `statusCode()`, `code()` and `details()`.
- `NetworkException` means the request never got an answer.
- `ValidationException` means the SDK rejected the input before sending.

Timeouts, network failures, 408, 429 and 5xx responses are retried up to 3 times with exponential backoff. Test-mode quota errors are not retried. Configure with `Relay.builder(key).maxRetries(n)`, `.retry(...)`, `.timeout(...)`, `.baseUrl(...)`, `.header(...)` and `.transport(...)`.

## Tracking in your app

```java
String token = relay.auth().createWebSocketToken(List.of("task:" + taskId)).token();
```

Hand it to your app, which tracks the task live with one of the app SDKs.

[Full documentation](https://docs.sendrelay.com.ng/sdks/java-sdk)
