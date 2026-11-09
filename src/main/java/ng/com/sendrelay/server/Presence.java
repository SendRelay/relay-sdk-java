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
        return get(latitude, longitude, resolution, null);
    }

    /**
     * Rider density around a point, counting only riders who could take the job {@code filter}
     * describes. The response then has {@code requirements}. A null filter is the plain map.
     */
    public Response get(double latitude, double longitude, String resolution, Filter filter) {
        Map<String, Object> query = new LinkedHashMap<>();
        // Plain decimals, never exponent notation.
        query.put("latitude", BigDecimal.valueOf(latitude).toPlainString());
        query.put("longitude", BigDecimal.valueOf(longitude).toPlainString());
        query.put("resolution", resolution);
        if (filter != null) {
            query.put("taskType", filter.taskType());
            if (filter.vehicleType() != null && !filter.vehicleType().isEmpty()) {
                query.put("vehicleType", String.join(",", filter.vehicleType()));
            }
            query.put("hasBox", filter.hasBox());
            query.put("hasRefrigeration", filter.hasRefrigeration());
            query.put("hasAirConditioning", filter.hasAirConditioning());
            query.put("estimatedWeight", filter.estimatedWeight());
            query.put("estimatedSize", filter.estimatedSize());
        }
        return relay.get("/sdk/presence", query, Response.class);
    }

    /**
     * A job to count riders for, in the quote's words. Null fields are left out: a null
     * {@code hasBox} lets Relay decide, and a null item class counts the smallest.
     */
    public record Filter(
            String taskType,
            List<String> vehicleType,
            Boolean hasBox,
            Boolean hasRefrigeration,
            Boolean hasAirConditioning,
            String estimatedWeight,
            String estimatedSize) {
        /** Riders who could take a job of this type, e.g. {@code Filter.of("FOOD_DELIVERY")}. */
        public static Filter of(String taskType) {
            return new Filter(taskType, null, null, null, null, null, null);
        }
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
            int refreshAfterSeconds,
            /* With a filter: what the counted riders have, as the quote would return it. */
            Tasks.Requirements requirements) {}
}
