package ng.com.sendrelay.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the shared SDK conformance fixtures (packages/sdks/conformance) against this SDK. Every
 * Relay SDK runs the same fixtures, which is how they stay at parity.
 */
class ConformanceTest {
    private static final Path DIR = Path.of(System.getenv().getOrDefault("RELAY_CONFORMANCE_DIR", "../conformance"));
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode read(String name) throws IOException {
        return JSON.readTree(DIR.resolve(name).toFile());
    }

    private static boolean available() {
        return Files.exists(DIR.resolve("manifest.json"));
    }

    private static <T> T as(JsonNode node, Class<T> type) {
        return node == null || node.isMissingNode() ? null : Relay.JSON.convertValue(node, type);
    }

    private static Integer intOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    /** Every manifest operation, bound to a call on this SDK. */
    private static final Map<String, BiFunction<Relay, JsonNode, Object>> BINDINGS = new HashMap<>();

    static {
        BINDINGS.put("tasks.quote", (r, i) -> r.tasks().quote(as(i.get("body"), Tasks.QuoteRequest.class)));
        BINDINGS.put("tasks.create", (r, i) -> r.tasks().create(as(i.get("body"), Tasks.CreateRequest.class), textOrNull(i.get("idempotencyKey"))));
        BINDINGS.put("tasks.list", (r, i) -> r.tasks().list(listParams(i.path("query"))));
        BINDINGS.put("tasks.listAll", (r, i) -> {
            List<String> ids = new ArrayList<>();
            for (Tasks.TaskListItem task : r.tasks().listAll(listParams(i.path("query")))) ids.add(task.id());
            return ids;
        });
        BINDINGS.put("tasks.get", (r, i) -> r.tasks().get(i.get("taskId").asText()));
        BINDINGS.put("tasks.cancel", (r, i) -> r.tasks().cancel(i.get("taskId").asText(), as(i.get("body"), Tasks.CancelRequest.class)));
        BINDINGS.put("tasks.assign", (r, i) -> r.tasks().assign(i.get("taskId").asText(), as(i.get("body"), Tasks.AssignRequest.class)));
        BINDINGS.put("tasks.availableRiders", (r, i) -> r.tasks().availableRiders(i.get("taskId").asText(), intOrNull(i.path("query").get("tier"))));
        BINDINGS.put("tasks.rate", (r, i) -> r.tasks().rate(i.get("taskId").asText(), as(i.get("body"), Tasks.RatingRequest.class)));
        BINDINGS.put("tasks.dispute", (r, i) -> r.tasks().dispute(i.get("taskId").asText(), as(i.get("body"), Tasks.DisputeRequest.class)));
        BINDINGS.put("webhooks.create", (r, i) -> r.webhooks().create(as(i.get("body"), Webhooks.CreateRequest.class)));
        BINDINGS.put("webhooks.list", (r, i) -> r.webhooks().list());
        BINDINGS.put("webhooks.get", (r, i) -> r.webhooks().get(i.get("webhookId").asText()));
        BINDINGS.put("webhooks.update", (r, i) -> r.webhooks().update(i.get("webhookId").asText(), as(i.get("body"), Webhooks.UpdateRequest.class)));
        BINDINGS.put("webhooks.delete", (r, i) -> r.webhooks().delete(i.get("webhookId").asText()));
        BINDINGS.put("auth.createWebSocketToken", (r, i) -> {
            List<String> scope = new ArrayList<>();
            i.path("body").path("scope").forEach(s -> scope.add(s.asText()));
            return r.auth().createWebSocketToken(scope, intOrNull(i.path("body").get("expiresIn")));
        });
        BINDINGS.put("presence.get", (r, i) -> {
            JsonNode q = i.get("query");
            return r.presence().get(q.get("latitude").asDouble(), q.get("longitude").asDouble(), textOrNull(q.get("resolution")));
        });
    }

    private static Tasks.ListParams listParams(JsonNode q) {
        return new Tasks.ListParams(intOrNull(q.get("limit")), textOrNull(q.get("order")), textOrNull(q.get("cursor")));
    }

    private static JsonNode lookup(JsonNode node, String dotted) {
        for (String key : dotted.split("\\.")) {
            if (node == null || node.isNull() || node.isMissingNode()) return NullNode.getInstance();
            node = node.isArray() ? node.get(Integer.parseInt(key)) : node.get(key);
        }
        return node == null || node.isMissingNode() ? NullNode.getInstance() : node;
    }

    private static void expectPaths(JsonNode actual, JsonNode expected) {
        expected.fields().forEachRemaining(entry -> {
            JsonNode got = lookup(actual, entry.getKey());
            JsonNode want = entry.getValue();
            boolean same = got.isNumber() && want.isNumber() ? got.asDouble() == want.asDouble() : got.equals(want);
            assertTrue(same, entry.getKey() + ": got " + got + ", want " + want);
        });
    }

    private static JsonNode errorJson(RelayException error) {
        Map<String, Object> out = new HashMap<>();
        out.put("message", error.getMessage());
        if (error instanceof ApiException api) {
            out.put("statusCode", api.statusCode());
            out.put("code", api.code());
            out.put("details", api.details());
        }
        return JSON.valueToTree(out);
    }

    private static String errorType(RelayException error) {
        if (error instanceof ApiException) return "ApiError";
        if (error instanceof NetworkException) return "NetworkError";
        if (error instanceof ValidationException) return "ValidationError";
        return error.getClass().getSimpleName();
    }

    /** Answers a fixture's exchanges in order and checks each request. */
    private static Transport exchanges(JsonNode fixture, JsonNode common, List<String> problems, int[] calls) {
        Deque<JsonNode> queue = new ArrayDeque<>();
        fixture.get("exchanges").forEach(queue::add);
        return request -> {
            calls[0]++;
            JsonNode want = queue.poll();
            if (want == null) {
                problems.add("unexpected request " + request.method() + " " + request.uri());
                return new Transport.Response(500, new byte[0]);
            }
            JsonNode expected = want.get("request");
            if (!request.method().equals(expected.get("method").asText())) problems.add("method " + request.method());
            if (!request.uri().getPath().equals(expected.get("path").asText())) problems.add("path " + request.uri().getPath());
            Map<String, String> query = new HashMap<>();
            if (request.uri().getRawQuery() != null) {
                for (String pair : request.uri().getRawQuery().split("&")) {
                    String[] kv = pair.split("=", 2);
                    query.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8), java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
            Map<String, String> wantQuery = new HashMap<>();
            expected.get("query").fields().forEachRemaining(e -> wantQuery.put(e.getKey(), e.getValue().asText()));
            if (!query.equals(wantQuery)) problems.add("query " + query + " != " + wantQuery);
            JsonNode body = request.body() == null ? NullNode.getInstance() : JSON.readTree(request.body());
            if (!body.equals(expected.get("body"))) problems.add("body\n  got  " + body + "\n  want " + expected.get("body"));
            Map<String, String> headers = new HashMap<>();
            common.fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
            expected.get("headers").fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
            headers.forEach((name, value) -> {
                String got = request.headers().get(name);
                switch (value) {
                    case "<absent>" -> {
                        if (got != null) problems.add("header " + name + " should be absent");
                    }
                    case "<any>" -> {
                        if (got == null || got.isEmpty()) problems.add("header " + name + " missing");
                    }
                    default -> {
                        if (!value.equals(got)) problems.add("header " + name + ": " + got);
                    }
                }
            });
            JsonNode response = want.get("response");
            return new Transport.Response(response.get("status").asInt(), JSON.writeValueAsBytes(response.get("body")));
        };
    }

    private static List<JsonNode> fixtures() throws IOException {
        List<JsonNode> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(DIR.resolve("http"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json")).sorted()::iterator) {
                out.add(JSON.readTree(file.toFile()));
            }
        }
        return out;
    }

    @Test
    void manifestIsFullyBound() throws IOException {
        assumeTrue(available(), "conformance folder not found");
        List<JsonNode> fixtures = fixtures();
        for (JsonNode op : read("manifest.json").path("server").path("operations")) {
            if (op.path("local").asBoolean()) continue;
            String id = op.get("id").asText();
            assertNotNull(BINDINGS.get(id), "missing binding for " + id);
            assertTrue(fixtures.stream().anyMatch(f -> f.get("operation").asText().equals(id)), "no fixture for " + id);
        }
    }

    @TestFactory
    Stream<DynamicTest> http() throws IOException {
        assumeTrue(available(), "conformance folder not found");
        JsonNode server = read("manifest.json").get("server");
        return fixtures().stream().map(f -> DynamicTest.dynamicTest(f.get("operation").asText() + ": " + f.get("name").asText(), () -> {
            List<String> problems = new ArrayList<>();
            int[] calls = {0};
            Relay relay = Relay.builder(server.get("apiKey").asText())
                    .baseUrl(server.get("baseUrl").asText())
                    .retry(RetryConfig.disabled())
                    .transport(exchanges(f, server.get("commonHeaders"), problems, calls))
                    .build();
            JsonNode expect = f.get("expect");
            try {
                Object result = BINDINGS.get(f.get("operation").asText()).apply(relay, f.get("input"));
                if (expect.has("error")) fail("expected " + expect.get("error") + ", got a result");
                JsonNode json = Relay.JSON.valueToTree(result);
                if (expect.has("items")) assertEquals(expect.get("items"), json);
                if (expect.has("result")) expectPaths(json, expect.get("result"));
            } catch (RelayException error) {
                if (!expect.has("error")) throw error;
                assertEquals(expect.get("error").get("type").asText(), errorType(error), error.toString());
                JsonNode fields = expect.get("error").deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) fields).remove("type");
                expectPaths(errorJson(error), fields);
            }
            assertTrue(problems.isEmpty(), String.join("\n", problems));
            assertEquals(f.get("exchanges").size(), calls[0], "requests sent");
        }));
    }

    @Test
    void defaults() throws IOException {
        assumeTrue(available(), "conformance folder not found");
        JsonNode d = read("client.json").get("defaults");
        Relay relay = Relay.builder("sk_test_abc").build();
        RetryConfig retry = relay.retry();
        assertEquals(d.get("baseUrl").asText(), relay.baseUrl());
        assertEquals(d.get("apiVersion").asText(), relay.apiVersion());
        assertEquals(d.get("timeoutMs").asLong(), relay.timeout().toMillis());
        assertEquals(d.path("retry").get("enabled").asBoolean(), retry.enabled());
        assertEquals(d.path("retry").get("maxRetries").asInt(), retry.maxRetries());
        assertEquals(d.path("retry").get("initialDelayMs").asLong(), retry.initialDelay().toMillis());
        assertEquals(d.path("retry").get("maxDelayMs").asLong(), retry.maxDelay().toMillis());
        assertEquals(d.path("retry").get("backoffMultiplier").asDouble(), retry.backoffMultiplier());
        List<Integer> codes = new ArrayList<>();
        d.path("retry").get("retryableStatusCodes").forEach(c -> codes.add(c.asInt()));
        assertEquals(codes, retry.retryableStatusCodes());
        assertEquals(d.path("idempotency").get("autoGenerate").asBoolean(), relay.autoIdempotency());
    }

    @TestFactory
    Stream<DynamicTest> apiKeys() throws IOException {
        assumeTrue(available(), "conformance folder not found");
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : read("client.json").get("apiKeys")) {
            tests.add(DynamicTest.dynamicTest("api key \"" + c.get("apiKey").asText() + "\"", () -> {
                if (c.has("error")) {
                    try {
                        Relay.builder(c.get("apiKey").asText()).build();
                        fail("expected a ValidationException");
                    } catch (ValidationException expected) {
                        // ok
                    }
                } else {
                    assertEquals(c.get("environment").asText(), Relay.builder(c.get("apiKey").asText()).build().environment());
                }
            }));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> errors() throws IOException {
        assumeTrue(available(), "conformance folder not found");
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : read("client.json").path("errors").get("cases")) {
            tests.add(DynamicTest.dynamicTest("errors: " + c.get("name").asText(), () -> {
                int[] calls = {0};
                Transport transport = request -> {
                    calls[0]++;
                    if (c.path("networkFailure").asBoolean()) throw new IOException("connection refused");
                    byte[] body = c.has("rawBody")
                            ? c.get("rawBody").asText().getBytes(StandardCharsets.UTF_8)
                            : JSON.writeValueAsBytes(c.get("body"));
                    return new Transport.Response(c.get("status").asInt(), body);
                };
                Relay relay = Relay.builder("sk_test_abc")
                        .retry(new RetryConfig(true, 1, Duration.ZERO, Duration.ZERO, 1, RetryConfig.defaults().retryableStatusCodes()))
                        .transport(transport)
                        .build();
                try {
                    relay.tasks().get("t_1");
                    fail("expected an error");
                } catch (RelayException error) {
                    assertEquals(c.get("expect").get("type").asText(), errorType(error));
                    JsonNode fields = c.get("expect").deepCopy();
                    ((com.fasterxml.jackson.databind.node.ObjectNode) fields).remove("type");
                    expectPaths(errorJson(error), fields);
                }
                assertEquals(c.get("attempts").asInt(), calls[0], "attempts");
            }));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> webhookSignatures() throws IOException {
        assumeTrue(available(), "conformance folder not found");
        List<DynamicTest> tests = new ArrayList<>();
        for (Iterator<JsonNode> it = read("webhooks/signatures.json").get("cases").elements(); it.hasNext(); ) {
            JsonNode c = it.next();
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
                Webhooks.Verification result = Webhooks.verifySignature(
                        c.get("payload").asText(), c.get("header").asText(), c.get("secret").asText(), Duration.ofSeconds(c.get("tolerance").asLong()));
                assertEquals(c.get("valid").asBoolean(), result.valid(), String.valueOf(result));
                if (!c.get("valid").asBoolean()) assertEquals(c.get("error").asText(), result.error());
                else assertFalse(result.error() != null);
            }));
        }
        return tests.stream();
    }

    @Test
    void validationErrorsHaveATypedField() {
        Relay relay = Relay.builder("sk_test_abc").transport(r -> fail("should not send")).build();
        ValidationException e = assertInstanceOf(ValidationException.class, catchIt(() -> relay.tasks().get("")));
        assertEquals("taskId", e.field());
    }

    private static RuntimeException catchIt(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            return e;
        }
        return null;
    }
}
