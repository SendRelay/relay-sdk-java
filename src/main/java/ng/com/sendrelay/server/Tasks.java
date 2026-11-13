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
        ListParams first = params == null ? new ListParams(null, null, null, null) : params;
        return () -> new Iterator<>() {
            private Iterator<TaskListItem> page = List.<TaskListItem>of().iterator();
            private String cursor = first.cursor();
            private boolean more = true;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && more) {
                    ListResponse response = list(new ListParams(first.limit(), first.order(), cursor, first.updatedSince()));
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

    /**
     * Change notes, externalId, or a stop's instructions and contact until that stop is completed.
     * Anything affecting price or dispatch is refused with FIELD_NOT_EDITABLE.
     */
    public UpdateResponse update(String taskId, UpdateRequest request) {
        return relay.patch("/sdk/tasks/" + Relay.pathId("taskId", taskId), request, UpdateResponse.class);
    }

    /** A hosted tracking page to send your customer; {@code request} may be null, or limit it to one DROPOFF. */
    public TrackingLinkResponse trackingLink(String taskId, TrackingLinkRequest request) {
        return relay.post(
                "/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/tracking-link",
                request == null ? new TrackingLinkRequest(null) : request,
                null,
                TrackingLinkResponse.class);
    }

    /** Every event about a task, oldest first, each with its {@code data.revision}. */
    public EventsResponse events(String taskId) {
        return relay.get("/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/events", null, EventsResponse.class);
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

    /**
     * Test keys only: open a SCHEDULED task's search now instead of at its {@code searchStartsAt}, so a
     * booking can be rehearsed without waiting for the pickup time.
     */
    public SimulateAdvanceResponse simulateAdvance(String taskId) {
        return relay.post("/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/simulate/advance", null, null, SimulateAdvanceResponse.class);
    }

    // ── Requests ─────────────────────────────────────────────────────────────

    /**
     * A place. {@code countryCode} (ISO 3166-1 alpha-2, for example "NG") and {@code postalCode} are
     * optional and not used in pricing yet.
     */
    public record Location(double latitude, double longitude, String address, String countryCode, String postalCode) {
        public Location(double latitude, double longitude, String address) {
            this(latitude, longitude, address, null, null);
        }
    }

    /** A parcel. {@code reference} is your own id for it, optional, so it can be followed across legs later. */
    public record Item(
            String name,
            String description,
            long estimatedValue,
            String estimatedWeight,
            String estimatedSize,
            String specialInstructions,
            String reference) {
        public Item(
                String name,
                String description,
                long estimatedValue,
                String estimatedWeight,
                String estimatedSize,
                String specialInstructions) {
            this(name, description, estimatedValue, estimatedWeight, estimatedSize, specialInstructions, null);
        }

        public Item(String name, long estimatedValue, String estimatedWeight, String estimatedSize) {
            this(name, null, estimatedValue, estimatedWeight, estimatedSize, null, null);
        }
    }

    public record Passenger(String name, String contactInfo, String specialNeeds, Integer luggageCount) {}

    /** A stop. On requests set type, location, instructions and items (or passengers); responses fill in progress. */
    /**
     * A stop. The last six components are newer: {@code contact} (who to call), {@code deliverItems}
     * (DROPOFF: item references handed over here), {@code completionCode} (a code the rider must enter
     * here; on create and get, the stop's code), and on responses {@code completionCodeLength},
     * {@code arrivedAt} and {@code waitSeconds}. The nine-argument constructor still works.
     */
    public record Stage(
            String type,
            Location location,
            String instructions,
            List<Item> items,
            List<Passenger> passengers,
            String status,
            String completedAt,
            String completionPhoto,
            String completionNotes,
            StageContact contact,
            List<String> deliverItems,
            StageCompletionCode completionCode,
            Integer completionCodeLength,
            String arrivedAt,
            Integer waitSeconds) {
        public Stage(
                String type,
                Location location,
                String instructions,
                List<Item> items,
                List<Passenger> passengers,
                String status,
                String completedAt,
                String completionPhoto,
                String completionNotes) {
            this(type, location, instructions, items, passengers, status, completedAt, completionPhoto, completionNotes, null, null, null, null, null, null);
        }

        /** The same stop with someone to call there. */
        public Stage withContact(StageContact value) {
            return new Stage(type, location, instructions, items, passengers, status, completedAt, completionPhoto, completionNotes, value, deliverItems, completionCode, completionCodeLength, arrivedAt, waitSeconds);
        }

        /** The same DROPOFF, handing over these item references. */
        public Stage withDeliverItems(List<String> value) {
            return new Stage(type, location, instructions, items, passengers, status, completedAt, completionPhoto, completionNotes, contact, value, completionCode, completionCodeLength, arrivedAt, waitSeconds);
        }

        /** The same stop, needing a code: {@code new StageCompletionCode("4821", null)} or {@code (null, 4)}. */
        public Stage withCompletionCode(StageCompletionCode value) {
            return new Stage(type, location, instructions, items, passengers, status, completedAt, completionPhoto, completionNotes, contact, deliverItems, value, completionCodeLength, arrivedAt, waitSeconds);
        }

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

    /** Proof of delivery: set {@code code} to choose the digits, or {@code length} (4 or 6) to have SendRelay generate them. */
    public record CompletionCodeRequest(String code, Integer length) {}

    /** Who to call at a stop. */
    public record StageContact(String name, String phone) {}

    /** A stop's code: {@code code} or {@code length} on a request; every field on a response. */
    public record StageCompletionCode(
            String code, Integer length, String source, String status, Integer failedAttempts, String lockedUntil, String verifiedAt) {
        public StageCompletionCode(String code, Integer length) {
            this(code, length, null, null, null, null, null);
        }
    }

    /**
     * When SendRelay looks for a rider; leave it null for IMMEDIATE. Part of the quote lock. Build it with
     * {@link #immediate()}, {@link #waitForRider(int)} or {@link #scheduled(String, Integer)}.
     */
    public record DispatchRequest(
            String strategy, Integer maxWaitMinutes, String pickupAt, Integer pickupWindowMinutes, String readyAt, String onRiderCancel) {
        public DispatchRequest(String strategy, Integer maxWaitMinutes, String pickupAt, Integer pickupWindowMinutes) {
            this(strategy, maxWaitMinutes, pickupAt, pickupWindowMinutes, null, null);
        }

        /**
         * IMMEDIATE and WAIT_FOR_RIDER: when the pickup will be ready (ISO 8601, at most 45 minutes
         * ahead). The search opens in time to arrive then; the task waits as SCHEDULED until it does.
         */
        public DispatchRequest withReadyAt(String value) {
            return new DispatchRequest(strategy, maxWaitMinutes, pickupAt, pickupWindowMinutes, value, onRiderCancel);
        }

        /** "CANCEL" (default: the task ends, refunded) or "FIND_ANOTHER" (look again, at most twice; needs autoAssign). */
        public DispatchRequest withOnRiderCancel(String value) {
            return new DispatchRequest(strategy, maxWaitMinutes, pickupAt, pickupWindowMinutes, readyAt, value);
        }

        /** Offer to nearby riders now (about two minutes). */
        public static DispatchRequest immediate() {
            return new DispatchRequest("IMMEDIATE", null, null, null);
        }

        /** Then keep offering as riders come online nearby, for 5–720 minutes (food 5–45). */
        public static DispatchRequest waitForRider(int maxWaitMinutes) {
            return new DispatchRequest("WAIT_FOR_RIDER", maxWaitMinutes, null, null);
        }

        /**
         * Book for {@code pickupAt} (ISO 8601 with an offset), 45 minutes to 7 days ahead, priced for then
         * and held now. {@code pickupWindowMinutes} may be null (30); at most 120.
         */
        public static DispatchRequest scheduled(String pickupAt, Integer pickupWindowMinutes) {
            return new DispatchRequest("SCHEDULED", null, pickupAt, pickupWindowMinutes);
        }
    }

    public record QuoteRequest(String taskType, String priority, List<Stage> stages, Preferences preferences, DispatchRequest dispatch, Boolean insured) {
        public QuoteRequest(String taskType, String priority, List<Stage> stages, Preferences preferences, DispatchRequest dispatch) {
            this(taskType, priority, stages, preferences, dispatch, null);
        }

        public QuoteRequest(String taskType, String priority, List<Stage> stages, Preferences preferences) {
            this(taskType, priority, stages, preferences, null, null);
        }

        public QuoteRequest(String taskType, List<Stage> stages) {
            this(taskType, null, stages, null, null, null);
        }

        /** The same request, with a dispatch strategy. */
        public QuoteRequest withDispatch(DispatchRequest value) {
            return new QuoteRequest(taskType, priority, stages, preferences, value, insured);
        }

        /**
         * The same request with insurance on the items' declared values turned on or off. It is on by
         * default; {@code false} means no premium, and loss or damage is not covered.
         */
        public QuoteRequest withInsured(boolean value) {
            return new QuoteRequest(taskType, priority, stages, preferences, dispatch, value);
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
            String simulationOutcome,
            DispatchRequest dispatch,
            Boolean insured,
            /* A completion photo at every DROPOFF; null for the default (no). */
            Boolean requirePhoto) {
        public CreateRequest(
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
                String simulationOutcome,
                DispatchRequest dispatch,
                Boolean insured) {
            this(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, simulationOutcome, dispatch, insured, null);
        }

        /** The same request, asking for a completion photo at every DROPOFF. */
        public CreateRequest withRequirePhoto(boolean value) {
            return new CreateRequest(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, simulationOutcome, dispatch, insured, value);
        }

        public CreateRequest(
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
                String simulationOutcome,
                DispatchRequest dispatch) {
            this(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, simulationOutcome, dispatch, null);
        }

        public CreateRequest(
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
            this(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, simulationOutcome, null);
        }

        public CreateRequest(String taskType, List<Stage> stages) {
            this(taskType, null, stages, null, null, null, null, null, null, null, null, null, null);
        }

        /** The same request, priced by a quote you got earlier. */
        public CreateRequest withQuoteId(String id) {
            return new CreateRequest(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, id, preferences, completionCode, simulationOutcome, dispatch, insured, requirePhoto);
        }

        /**
         * The same request, for a test key: how the simulated delivery plays out (SUCCESS, NO_RIDERS, ...).
         * SEARCH_THEN_ASSIGNED and SEARCH_EXPIRED need WAIT_FOR_RIDER or SCHEDULED.
         */
        public CreateRequest withSimulationOutcome(String outcome) {
            return new CreateRequest(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, outcome, dispatch, insured, requirePhoto);
        }

        /** The same request, with a dispatch strategy (send the same one you quoted). */
        public CreateRequest withDispatch(DispatchRequest value) {
            return new CreateRequest(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, simulationOutcome, value, insured);
        }

        /**
         * The same request with insurance on the items' declared values turned on or off. It is on by
         * default; {@code false} means no premium, and loss or damage is not covered. Send the same
         * value you quoted.
         */
        public CreateRequest withInsured(boolean value) {
            return new CreateRequest(taskType, priority, stages, autoAssign, paymentMethod, notes, externalId, quoteId, preferences, completionCode, simulationOutcome, dispatch, value);
        }
    }

    /**
     * Filters for {@link #list}. Any may be null. {@code order} is "asc" or "desc" (default).
     * {@code updatedSince} (ISO 8601) lists only tasks changed at or after that time, in
     * {@code updatedAt} order: for reconciliation, walk forward with "asc".
     */
    public record ListParams(Integer limit, String order, String cursor, String updatedSince) {
        public ListParams(Integer limit, String order, String cursor) {
            this(limit, order, cursor, null);
        }

        Map<String, Object> toQuery() {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("limit", limit);
            query.put("order", order);
            query.put("cursor", cursor);
            query.put("updatedSince", updatedSince);
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
            Rider rider,
            /* When SendRelay looks for a rider, and for how long. */
            Dispatch dispatch,
            /* Goes up with every event about the task and matches events' data.revision; 0 before the first. */
            int revision,
            /* Currency of every amount, in minor units (kobo for NGN). */
            String currency,
            /* A completion photo is required at every DROPOFF. */
            boolean requirePhoto,
            /* The vehicles SendRelay looks for and the equipment a rider on each needs. */
            Requirements requirements) {}

    /**
     * What SendRelay looks for in a rider: each vehicle that can take the task, with the equipment a rider
     * on it needs (for example a box on a motorcycle for food). Every vehicle is listed when the task
     * names none.
     */
    public record Requirements(List<RequirementVehicle> vehicles) {}

    /** {@code equipment} holds BOX, REFRIGERATION or AIR_CONDITIONING; treat others as equipment you can't check. */
    public record RequirementVehicle(String vehicleType, List<String> equipment) {}

    /**
     * The dispatch block on every task. {@code strategy} is IMMEDIATE, WAIT_FOR_RIDER or SCHEDULED, or a
     * newer one as its raw string. {@code searchExpiresAt} is null for IMMEDIATE.
     */
    public record Dispatch(
            String strategy,
            Integer maxWaitMinutes,
            String pickupAt,
            Integer pickupWindowMinutes,
            String searchStartsAt,
            String searchExpiresAt,
            int offerRounds,
            String readyAt,
            String onRiderCancel) {}

    /** The reply to {@link #simulateAdvance}: still SCHEDULED; the task moves to PENDING within seconds. */
    public record SimulateAdvanceResponse(String message, String taskId, String status, String searchStartsAt, String searchExpiresAt) {}

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
    public record Pricing(long total, long subtotal, long tax, long platformFee, long priorityFee, long insuranceFee, long insuredValue, JsonNode lines) {
        @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
        static Pricing from(JsonNode node) {
            return new Pricing(
                    node.path("total").asLong(),
                    node.path("subtotal").asLong(),
                    node.path("tax").asLong(),
                    node.path("platformFee").asLong(),
                    node.path("priorityFee").asLong(),
                    node.path("insuranceFee").asLong(),
                    node.path("insuredValue").asLong(),
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
            long insuranceFee,
            long insuredValue,
            String pricingVehicleType,
            String quoteId,
            String expiresAt,
            int quoteTtlSeconds,
            QuoteRequest task,
            /* What the task will need, as create will store it. */
            Requirements requirements) {}

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
            Integer completionCodeLength,
            String updatedAt,
            int revision,
            String currency) {}

    public record ListResponse(List<TaskListItem> data, boolean hasMore, String cursor) {}

    /** Every event about one task, oldest first. */
    public record EventsResponse(List<Events.Event> data) {}

    /** What {@link #update} may change. {@code contact} null on a stage leaves it unchanged. */
    public record UpdateRequest(String notes, String externalId, List<UpdateStage> stages) {}

    public record UpdateStage(int index, String instructions, StageContact contact) {}

    public record UpdateResponse(Task task) {}

    public record TrackingLinkRequest(Integer stageIndex) {}

    public record TrackingLinkResponse(String url, String expiresAt, Integer stageIndex) {}

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
