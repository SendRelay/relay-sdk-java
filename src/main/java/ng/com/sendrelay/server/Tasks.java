package ng.com.sendrelay.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Quote, create and manage deliveries. Amounts are in kobo (100 kobo = ₦1). Timestamps are ISO 8601
 * strings, as the API sends them. Optional fields are nullable; pass null to leave them out.
 */
public final class Tasks {
    private final Relay relay;

    Tasks(Relay relay) {
        this.relay = relay;
    }

    // ── Operations ───────────────────────────────────────────────────────────

    /** Price a delivery without creating it. Pass {@code quoteId} to {@link #create} to lock the price. */
    public QuoteResponse quote(QuoteRequest request) {
        return relay.post("/sdk/tasks/quote", request, null, QuoteResponse.class);
    }

    /** Create a delivery, generating an idempotency key for this call. */
    public CreateResponse create(CreateRequest request) {
        return create(request, null);
    }

    /**
     * Create a delivery. Pass your order ID as {@code idempotencyKey} so a retry never creates a
     * second delivery.
     */
    public CreateResponse create(CreateRequest request, String idempotencyKey) {
        return relay.post("/sdk/tasks", request, idempotencyKey, CreateResponse.class);
    }

    /** One page of tasks, newest first by default. {@code params} may be null. */
    public ListResponse list(ListParams params) {
        return relay.get("/sdk/tasks", params == null ? null : params.toQuery(), ListResponse.class);
    }

    /** Every task, fetched page by page as you iterate. */
    public Iterable<TaskListItem> listAll(ListParams params) {
        ListParams first = params == null ? new ListParams(null, null, null) : params;
        return () -> new Iterator<>() {
            private Iterator<TaskListItem> page = List.<TaskListItem>of().iterator();
            private String cursor = first.cursor();
            private boolean more = true;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && more) {
                    ListResponse response = list(new ListParams(first.limit(), first.order(), cursor));
                    page = response.data().iterator();
                    cursor = response.cursor();
                    more = response.hasMore() && cursor != null && !cursor.isEmpty();
                }
                return page.hasNext();
            }

            @Override
            public TaskListItem next() {
                if (!hasNext()) throw new NoSuchElementException();
                return page.next();
            }
        };
    }

    /** A task, your rating of it, and the rider's live ETA while they are on the way. */
    public GetResponse get(String taskId) {
        return relay.get("/sdk/tasks/" + Relay.pathId("taskId", taskId), null, GetResponse.class);
    }

    /** Cancel a task that hasn't been picked up. {@code request} may be null. */
    public CancelResponse cancel(String taskId, CancelRequest request) {
        return relay.delete("/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/cancel", request, CancelResponse.class);
    }

    /** Offer a manual ({@code autoAssign: false}) live task to riders from {@link #availableRiders}. */
    public AssignResponse assign(String taskId, AssignRequest request) {
        return relay.post("/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/assign", request, null, AssignResponse.class);
    }

    /** Riders you can offer a manual task to. {@code tier} is 1, 2 or 3; null means 1. */
    public AvailableRidersResponse availableRiders(String taskId, Integer tier) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("tier", tier);
        return relay.get("/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/available-riders", query, AvailableRidersResponse.class);
    }

    /** Rate the rider of a completed task, once. Check {@link GetResponse#rating()} first. */
    public RatingResponse rate(String taskId, RatingRequest request) {
        return relay.post("/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/ratings", request, null, RatingResponse.class);
    }

    /** Open a dispute on a completed task within the dispute window. */
    public DisputeResponse dispute(String taskId, DisputeRequest request) {
        return relay.post("/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/dispute", request, null, DisputeResponse.class);
    }

    // ── Requests ─────────────────────────────────────────────────────────────

    public record Location(double latitude, double longitude, String address) {}

    public record Item(
            String name,
            String description,
            long estimatedValue,
            String estimatedWeight,
            String estimatedSize,
            String specialInstructions) {
        public Item(String name, long estimatedValue, String estimatedWeight, String estimatedSize) {
            this(name, null, estimatedValue, estimatedWeight, estimatedSize, null);
        }
    }

    public record Passenger(String name, String contactInfo, String specialNeeds, Integer luggageCount) {}

    /** A stop. On requests set type, location, instructions and items (or passengers); responses fill in progress. */
    public record Stage(
            String type,
            Location location,
            String instructions,
            List<Item> items,
            List<Passenger> passengers,
            String status,
            String completedAt,
            String completionPhoto,
            String completionNotes) {
        public static Stage pickup(Location location, String instructions, List<Item> items) {
            return new Stage("PICKUP", location, instructions, items, null, null, null, null, null);
        }

        public static Stage dropoff(Location location, String instructions) {
            return new Stage("DROPOFF", location, instructions, null, null, null, null, null, null);
        }
    }

    public record Preferences(
            Double minRating,
            Boolean hasBox,
            Boolean hasRefrigeration,
            Boolean hasAirConditioning,
            List<String> vehicleType,
            List<String> allowedOrganisations,
            List<String> blockedOrganisations,
            Boolean allowIndependentRiders) {}

    /** Proof of delivery: set {@code code} to choose the digits, or {@code length} (4 or 6) to have Relay generate them. */
    public record CompletionCodeRequest(String code, Integer length) {}

    public record QuoteRequest(String taskType, String priority, List<Stage> stages, Preferences preferences) {
        public QuoteRequest(String taskType, List<Stage> stages) {
            this(taskType, null, stages, null);
        }
    }

    public record CreateRequest(
            String taskType,
            String priority,
            List<Stage> stages,
            Boolean autoAssign,
            String paymentMethod,
            String notes,
            String externalId,
            String quoteId,
            Preferences preferences,
            CompletionCodeRequest completionCode,
            String simulationOutcome) {
        public CreateRequest(String taskType, List<Stage> stages) {
            this(taskType, null, stages, null, null, null, null, null, null, null, null);
        }

        /** The same request, priced by a quote you got earlier. */
        public CreateRequest withQuoteId(String id) {
            return new CreateRequest(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, id, preferences, completionCode, simulationOutcome);
        }

        /** The same request, for a test key: how the simulated delivery plays out (SUCCESS, NO_RIDERS, ...). */
        public CreateRequest withSimulationOutcome(String outcome) {
            return new CreateRequest(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, outcome);
        }
    }

    /** Filters for {@link #list}. Any may be null. {@code order} is "asc" or "desc" (default). */
    public record ListParams(Integer limit, String order, String cursor) {
        Map<String, Object> toQuery() {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("limit", limit);
            query.put("order", order);
            query.put("cursor", cursor);
            return query;
        }
    }

    public record CancelRequest(String reason) {}

    /** Riders from {@link #availableRiders} (at most 5 are used), and how long they have to accept (15–300 s). */
    public record AssignRequest(List<String> riderIds, Integer offerTimeoutSeconds) {}

    public record RatingRequest(int rating, String comment) {}

    /** {@code reason}: NOT_DELIVERED, DAMAGED_ITEMS, WRONG_LOCATION, INCOMPLETE_DELIVERY, POOR_SERVICE or OTHER. */
    public record DisputeRequest(String reason, String description, List<String> evidence) {}

    // ── Responses ────────────────────────────────────────────────────────────

    public record RatingSummary(double average, int totalReviews) {}

    public record Rider(String id, String firstName, String lastName, String phone, String photo, JsonNode vehicle, RatingSummary rating) {}

    public record Routing(
            String provider,
            String pricingBasis,
            String generatedAt,
            long distanceMeters,
            long durationSeconds,
            long durationInTrafficSeconds,
            long tollCostKobo,
            int stagesCount,
            String routePolyline) {}

    public record CompletionCode(
            String code, int length, String source, String status, int failedAttempts, String lockedUntil, String verifiedAt) {}

    public record Task(
            String id,
            String developerId,
            String riderId,
            String externalId,
            String status,
            String taskType,
            String priority,
            String source,
            boolean autoAssign,
            Integer currentStageIndex,
            String notes,
            Preferences preferences,
            List<Stage> stages,
            long deliveryFee,
            long totalFee,
            String paymentStatus,
            String paymentMethod,
            JsonNode pricing,
            String createdAt,
            String updatedAt,
            String offeredAt,
            String acceptedAt,
            String assignedAt,
            String completedAt,
            String failedAt,
            String cancelledAt,
            String failureReason,
            String disputeId,
            Routing routing,
            String routePolyline,
            boolean completionCodeRequired,
            Integer completionCodeLength,
            CompletionCode completionCode,
            Rider rider) {}

    /** How far the rider is from the stop they are heading to, from their navigation route and live traffic. */
    public record LiveEta(int stageIndex, int seconds, int meters, String arrivesAt, String updatedAt) {}

    /** Your rating of a completed task's rider. */
    public record Rating(int rating, String comment, String createdAt) {}

    public record GetResponse(
            Task task,
            /* Your rating of this task's rider, or null if you haven't rated it. */
            Rating rating,
            /* The rider's ETA to their next stop, or null when no rider is on the way. */
            LiveEta liveEta) {}

    /**
     * A price. {@link #lines()} has every fee line as the API sent it, including ones not named here
     * (insuranceFee, temporalFee, ...).
     */
    public record Pricing(long total, long subtotal, long tax, long platformFee, long priorityFee, JsonNode lines) {
        @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
        static Pricing from(JsonNode node) {
            return new Pricing(
                    node.path("total").asLong(),
                    node.path("subtotal").asLong(),
                    node.path("tax").asLong(),
                    node.path("platformFee").asLong(),
                    node.path("priorityFee").asLong(),
                    node);
        }

        @com.fasterxml.jackson.annotation.JsonValue
        JsonNode toJson() {
            return lines;
        }
    }

    public record QuoteResponse(
            long total,
            long subtotal,
            long tax,
            long platformFee,
            long priorityFee,
            String pricingVehicleType,
            String quoteId,
            String expiresAt,
            int quoteTtlSeconds,
            QuoteRequest task) {}

    public record CreateResponse(String message, Task task, Pricing pricing, JsonNode idempotency, JsonNode simulation) {}

    public record TaskListItem(
            String id,
            String developerId,
            String riderId,
            String externalId,
            String status,
            String taskType,
            String priority,
            boolean autoAssign,
            int stages,
            long deliveryFee,
            long totalFee,
            String createdAt,
            boolean completionCodeRequired,
            Integer completionCodeLength) {}

    public record ListResponse(List<TaskListItem> data, boolean hasMore, String cursor) {}

    public record CancelResponse(String message, String taskId, String status) {}

    public record RejectedRider(String riderId, String reason) {}

    public record Assignment(
            String offerId,
            List<String> submittedRiderIds,
            List<String> acceptedRiderIds,
            List<RejectedRider> rejectedRiders,
            int requestedRiderCount,
            int acceptedRiderCount,
            int maxRiders,
            boolean truncated,
            int offerTimeoutSeconds) {}

    public record AssignResponse(String message, String taskId, String status, String workflowInstanceId, Assignment assignment) {}

    public record AvailableRider(String riderId, String firstName, double distanceKm, JsonNode vehicle, RatingSummary rating) {}

    public record AvailableRidersResponse(String taskId, int tier, int maxAssignableRiders, List<AvailableRider> riders) {}

    public record RatingResponse(String ratingId, int rating, String comment) {}

    public record DisputeResponse(String message, String disputeId, String status, String note, String reviewTimeframe) {}
}
