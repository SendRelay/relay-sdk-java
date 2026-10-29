package ng.com.sendrelay.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Relay server SDK for Java and Kotlin: quote and create deliveries, read them back with the
 * rider's live ETA, manage webhooks and mint tracking tokens for your apps. Keep the API key on
 * your server.
 *
 * <pre>{@code
 * Relay relay = Relay.builder(System.getenv("RELAY_API_KEY")).build();
 * Tasks.QuoteResponse quote = relay.tasks().quote(new Tasks.QuoteRequest("PACKAGE_DELIVERY", stages));
 * }</pre>
 *
 * Calls are blocking and the client is thread-safe: share one instance. On Java 21, run calls on
 * virtual threads for concurrency.
 */
public final class Relay {
    /** The published SDK version. Kept in sync with gradle.properties by scripts/check-sdk-versions.mjs. */
    public static final String VERSION = "1.7.0";

    public static final String DEFAULT_BASE_URL = "https://api.sendrelay.com.ng";
    public static final String DEFAULT_API_VERSION = "v1";

    // Test-mode quota 429s last minutes to hours; retrying can't succeed, so surface them at once.
    private static final Set<String> NON_RETRYABLE_ERROR_CODES = Set.of("TEST_QUOTA_EXCEEDED", "TEST_CONCURRENCY_EXCEEDED");
    private static final SecureRandom RANDOM = new SecureRandom();

    static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private final String apiKey;
    private final String baseUrl;
    private final String apiVersion;
    private final Duration timeout;
    private final RetryConfig retry;
    private final boolean autoIdempotency;
    private final Map<String, String> headers;
    private final Transport transport;

    private final Tasks tasks = new Tasks(this);
    private final Webhooks webhooks = new Webhooks(this);
    private final Auth auth = new Auth(this);
    private final Presence presence = new Presence(this);

    private Relay(Builder b) {
        this.apiKey = b.apiKey;
        this.baseUrl = b.baseUrl.replaceAll("/+$", "");
        this.apiVersion = b.apiVersion;
        this.timeout = b.timeout;
        this.retry = b.retry;
        this.autoIdempotency = b.autoIdempotency;
        this.headers = Map.copyOf(b.headers);
        this.transport = b.transport != null ? b.transport : defaultTransport();
    }

    /** Start configuring a client for a live ({@code sk_live_...}) or test ({@code sk_test_...}) key. */
    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    public Tasks tasks() {
        return tasks;
    }

    public Webhooks webhooks() {
        return webhooks;
    }

    public Auth auth() {
        return auth;
    }

    public Presence presence() {
        return presence;
    }

    /** {@code "live"} for a live key, {@code "test"} for a test key. */
    public String environment() {
        return apiKey.startsWith("sk_live_") ? "live" : "test";
    }

    Duration timeout() {
        return timeout;
    }

    RetryConfig retry() {
        return retry;
    }

    boolean autoIdempotency() {
        return autoIdempotency;
    }

    String baseUrl() {
        return baseUrl;
    }

    String apiVersion() {
        return apiVersion;
    }

    public static final class Builder {
        private final String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private String apiVersion = DEFAULT_API_VERSION;
        private Duration timeout = Duration.ofSeconds(30);
        private RetryConfig retry = RetryConfig.defaults();
        private boolean autoIdempotency = true;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Transport transport;

        private Builder(String apiKey) {
            this.apiKey = apiKey;
        }

        /** Point the client at another deployment, such as a local API. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /** Per-request timeout (default 30 seconds). */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** How many times a failed request is retried (default 3). */
        public Builder maxRetries(int maxRetries) {
            this.retry = retry.withMaxRetries(maxRetries);
            return this;
        }

        public Builder retry(RetryConfig retry) {
            this.retry = retry;
            return this;
        }

        /** Stop generating an Idempotency-Key for POST requests you don't give one. */
        public Builder autoIdempotency(boolean enabled) {
            this.autoIdempotency = enabled;
            return this;
        }

        /** Add a header to every request. */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Send requests through your own transport, for proxies, tracing or tests. */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public Relay build() {
            if (apiKey == null || apiKey.isEmpty()) throw new ValidationException("API key is required", "apiKey");
            if (!apiKey.startsWith("sk_live_") && !apiKey.startsWith("sk_test_")) {
                throw new ValidationException("Invalid API key format. Must start with sk_live_ or sk_test_", "apiKey");
            }
            return new Relay(this);
        }
    }

    // ── Requests ─────────────────────────────────────────────────────────────

    static String pathId(String name, String value) {
        if (value == null || value.isEmpty()) throw new ValidationException(name + " is required", name);
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    <T> T get(String path, Map<String, ?> query, Class<T> type) {
        return send("GET", path, query, null, null, type);
    }

    <T> T post(String path, Object body, String idempotencyKey, Class<T> type) {
        return send("POST", path, null, body, idempotencyKey, type);
    }

    <T> T put(String path, Object body, Class<T> type) {
        return send("PUT", path, null, body, null, type);
    }

    <T> T delete(String path, Object body, Class<T> type) {
        return send("DELETE", path, null, body, null, type);
    }

    private <T> T send(String method, String path, Map<String, ?> query, Object body, String idempotencyKey, Class<T> type) {
        URI uri = URI.create(baseUrl + "/" + apiVersion + path + queryString(query));
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("X-Relay-Key", apiKey);
        requestHeaders.put("User-Agent", "relay-sdk-java/" + VERSION);
        requestHeaders.putAll(headers);
        if (idempotencyKey != null) {
            requestHeaders.put("Idempotency-Key", idempotencyKey);
        } else if (method.equals("POST") && autoIdempotency) {
            byte[] bytes = new byte[16];
            RANDOM.nextBytes(bytes);
            requestHeaders.put("Idempotency-Key", "sdk-" + System.currentTimeMillis() + "-" + HexFormat.of().formatHex(bytes));
        }
        byte[] payload;
        try {
            payload = body == null ? null : JSON.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RelayException("Could not encode the request: " + e.getMessage(), e);
        }
        Transport.Request request = new Transport.Request(method, uri, Map.copyOf(requestHeaders), payload, timeout);

        for (int attempt = 0; ; attempt++) {
            Transport.Response response;
            try {
                response = transport.send(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NetworkException("Request interrupted", e);
            } catch (IOException e) {
                if (retry.enabled() && attempt < retry.maxRetries()) {
                    pause(retry.delayMillis(attempt));
                    continue;
                }
                String message = e instanceof HttpTimeoutException
                        ? "Request timeout after " + timeout.toMillis() + "ms"
                        : "Network error - connection failed";
                throw new NetworkException(message, e);
            }

            if (response.status() >= 200 && response.status() < 300) {
                try {
                    return type == Void.class || response.body().length == 0 ? null : JSON.readValue(response.body(), type);
                } catch (IOException e) {
                    throw new RelayException("Could not read the response: " + e.getMessage(), e);
                }
            }

            ApiException error = toApiException(response);
            boolean retryable = retry.enabled()
                    && attempt < retry.maxRetries()
                    && !NON_RETRYABLE_ERROR_CODES.contains(error.code())
                    && retry.retryableStatusCodes().contains(error.statusCode());
            if (!retryable) throw error;
            pause(retry.delayMillis(attempt));
        }
    }

    private static ApiException toApiException(Transport.Response response) {
        JsonNode error = null;
        try {
            error = JSON.readTree(response.body()).path("error");
        } catch (IOException ignored) {
            // Not JSON (a proxy's HTML page, say): fall back to the status.
        }
        String code = error != null && error.hasNonNull("code") ? error.get("code").asText() : "UNKNOWN_ERROR";
        String message = error != null && error.hasNonNull("message") ? error.get("message").asText() : "HTTP " + response.status();
        JsonNode details = error != null && error.hasNonNull("details") ? error.get("details") : null;
        return new ApiException(response.status(), code, message, details);
    }

    private static String queryString(Map<String, ?> query) {
        if (query == null || query.isEmpty()) return "";
        String joined = query.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> encode(e.getKey()) + "=" + encode(String.valueOf(e.getValue())))
                .collect(Collectors.joining("&"));
        return joined.isEmpty() ? "" : "?" + joined;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("Request interrupted", e);
        }
    }

    private static Transport defaultTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return request -> {
            HttpRequest.BodyPublisher body = request.body() == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(request.body());
            HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(request.timeout()).method(request.method(), body);
            // java.net.http sets User-Agent itself only when we don't.
            request.headers().forEach(builder::setHeader);
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Transport.Response(response.statusCode(), response.body());
        };
    }
}
