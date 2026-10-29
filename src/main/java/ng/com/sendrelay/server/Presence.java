package ng.com.sendrelay.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anonymous rider availability around a point, for maps shown before creating a task. */
public final class Presence {
    private final Relay relay;

    Presence(Relay relay) {
        this.relay = relay;
    }

    /** Rider density around a point. {@code resolution} is BLOCK, NEIGHBOURHOOD or DISTRICT; null means NEIGHBOURHOOD. */
    public Response get(double latitude, double longitude, String resolution) {
        Map<String, Object> query = new LinkedHashMap<>();
        // Plain decimals, never exponent notation.
        query.put("latitude", BigDecimal.valueOf(latitude).toPlainString());
        query.put("longitude", BigDecimal.valueOf(longitude).toPlainString());
        query.put("resolution", resolution);
        return relay.get("/sdk/presence", query, Response.class);
    }

    public record Point(double latitude, double longitude) {}

    public record Cell(
            String cellId, String resolution, int h3Resolution, Point center, List<Point> boundary, String density, List<String> vehicleTypes) {}

    public record Response(
            String mode,
            boolean simulated,
            String resolution,
            int h3Resolution,
            JsonNode area,
            List<Cell> cells,
            JsonNode totals,
            int minRidersPerCell,
            boolean capped,
            String generatedAt,
            int refreshAfterSeconds) {}
}
