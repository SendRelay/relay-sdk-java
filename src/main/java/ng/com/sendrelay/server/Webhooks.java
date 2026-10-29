package ng.com.sendrelay.server;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Manage the endpoints Relay sends events to, and check that deliveries came from Relay. */
public final class Webhooks {
    /** How old a delivery may be before {@link #verifySignature} rejects it. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);

    private final Relay relay;

    Webhooks(Relay relay) {
        this.relay = relay;
    }

    /** Register an endpoint. Store {@link CreateResponse#secret()}: it is returned only once. */
    public CreateResponse create(CreateRequest request) {
        return relay.post("/sdk/webhooks", request, null, CreateResponse.class);
    }

    public ListResponse list() {
        return relay.get("/sdk/webhooks", null, ListResponse.class);
    }

    public Webhook get(String webhookId) {
        return relay.get("/sdk/webhooks/" + Relay.pathId("webhookId", webhookId), null, Webhook.class);
    }

    public Webhook update(String webhookId, UpdateRequest request) {
        return relay.put("/sdk/webhooks/" + Relay.pathId("webhookId", webhookId), request, Webhook.class);
    }

    /** Remove an endpoint. Returns it as it was. */
    public Webhook delete(String webhookId) {
        return relay.delete("/sdk/webhooks/" + Relay.pathId("webhookId", webhookId), null, Webhook.class);
    }

    /**
     * Check that a delivery came from Relay and wasn't changed on the way. Pass the raw request body
     * exactly as received, the {@code X-Relay-Signature} header and the {@code whsec_} secret.
     */
    public static Verification verifySignature(String payload, String signatureHeader, String secret, Duration tolerance) {
        String timestampPart = null;
        String signature = null;
        for (String part : (signatureHeader == null ? "" : signatureHeader).split(",")) {
            if (part.startsWith("t=") && timestampPart == null) timestampPart = part.substring(2);
            else if (part.startsWith("v1=") && signature == null) signature = part.substring(3);
        }
        if (timestampPart == null || signature == null) return Verification.failed("Invalid signature format");

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampPart);
        } catch (NumberFormatException e) {
            return Verification.failed("Invalid signature format");
        }
        long age = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (age > tolerance.getSeconds()) return Verification.failed("Timestamp outside tolerance window");

        try {
            // The key is everything after `whsec_` (mode prefix included), base64url-decoded.
            byte[] key = Base64.getUrlDecoder().decode(secret.replaceFirst("whsec_", "").replaceAll("=+$", ""));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
                return Verification.failed("Signature verification failed");
            }
            return new Verification(true, null);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeyException e) {
            return Verification.failed("Invalid webhook secret");
        }
    }

    /** {@link #verifySignature(String, String, String, Duration)} with the default 300-second tolerance. */
    public static Verification verifySignature(String payload, String signatureHeader, String secret) {
        return verifySignature(payload, signatureHeader, secret, DEFAULT_TOLERANCE);
    }

    /** The result of a signature check. {@code error} says why it was rejected. */
    public record Verification(boolean valid, String error) {
        static Verification failed(String error) {
            return new Verification(false, error);
        }
    }

    /** {@code mode} is "live" (default) or "test", and must match your API key. */
    public record CreateRequest(String url, List<String> events, String description, String mode) {}

    public record UpdateRequest(String url, List<String> events, String description) {}

    public record Usage(int successCount, int errorCount, String lastUsed) {}

    public record Webhook(String id, String url, String mode, String description, String createdAt, List<String> events, Usage usage) {}

    public record CreateResponse(String id, String url, String mode, List<String> events, String secret) {}

    public record ListResponse(List<Webhook> data, boolean hasMore, String cursor) {}
}
