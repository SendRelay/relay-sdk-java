package ng.com.sendrelay.server;

import java.util.List;

/** Mint tokens for apps to track tasks live. */
public final class Auth {
    private final Relay relay;

    Auth(Relay relay) {
        this.relay = relay;
    }

    /**
     * A short-lived token for one of the app SDKs. Scope it to the tasks the app may see:
     * {@code List.of("task:" + taskId)} (up to 5 IDs per entry, comma-separated). {@code expiresIn}
     * is 60–7200 seconds; null means 1800.
     */
    public WebSocketToken createWebSocketToken(List<String> scope, Integer expiresIn) {
        if (scope == null || scope.isEmpty()) throw new ValidationException("scope must contain at least one resource", "scope");
        for (String resource : scope) {
            String[] parts = resource.split(":", -1);
            if (parts.length != 2) {
                throw new ValidationException("Invalid scope format: \"" + resource + "\". Expected \"type:id\" (e.g., \"task:abc123\")", "scope");
            }
            if (parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new ValidationException("Invalid scope format: \"" + resource + "\". Both type and id must be non-empty", "scope");
            }
            if (parts[0].equals("task") && parts[1].split(",").length > 5) {
                throw new ValidationException("Scoped tasks cannot exceed 5 per scope entry: \"" + resource + "\"", "scope");
            }
        }
        if (expiresIn != null && (expiresIn < 60 || expiresIn > 7200)) {
            throw new ValidationException("expiresIn must be between 60 and 7200 seconds", "expiresIn");
        }
        return relay.post("/auth/websocket-token", new TokenRequest(scope, expiresIn == null ? 1800 : expiresIn), null, WebSocketToken.class);
    }

    public WebSocketToken createWebSocketToken(List<String> scope) {
        return createWebSocketToken(scope, null);
    }

    record TokenRequest(List<String> scope, int expiresIn) {}

    public record WebSocketToken(String token, int expiresIn, String expiresAt, List<String> scope) {}
}
