package ng.com.sendrelay.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;

/**
 * Errands: a rider queues, buys, pays a bill, checks something or does a task you describe, at one or
 * more stops ({@code taskType} ERRAND). Each job is one stage. The builders here return the same
 * {@link Tasks.Stage} you could write by hand; they only save remembering which fields each type takes.
 *
 * <pre>{@code
 * relay.tasks().create(new Tasks.CreateRequest("ERRAND", List.of(
 *         Errand.queue(bank, "Collect the hardware token", Errand.work().withExpectedMinutes(30).withMaxMinutes(90)),
 *         Errand.purchase(shoprite, "", List.of(new Errand.PurchaseItem("Peak milk 400g", 2)), Errand.work().withSpendLimit(2_500_000L)),
 *         Errand.dropoff(home, "").withContact(new Tasks.StageContact("Ada", "+2348030000000")))));
 * }</pre>
 *
 * See https://docs.sendrelay.com.ng/errands. Amounts are in kobo.
 */
public final class Errand {
    private Errand() {}

    // ── Builders ─────────────────────────────────────────────────────────────

    /** No errand fields yet; add them with the {@code with...} methods. */
    public static Work work() {
        return new Work(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Collect items to carry, as on any delivery. */
    public static Tasks.Stage pickup(Tasks.Location location, String instructions, List<Tasks.Item> items) {
        return Tasks.Stage.pickup(location, instructions, items);
    }

    /** Hand over what was collected or bought. Without {@code deliverItems}, everything comes here. */
    public static Tasks.Stage dropoff(Tasks.Location location, String instructions) {
        return Tasks.Stage.dropoff(location, instructions);
    }

    /** Wait in line or for a service, then report back. */
    public static Tasks.Stage queue(Tasks.Location location, String instructions, Work work) {
        return stage("QUEUE", location, instructions, work);
    }

    /** Buy a shopping list, within the prices you set. */
    public static Tasks.Stage purchase(Tasks.Location location, String instructions, List<PurchaseItem> items, Work work) {
        return stage("PURCHASE", location, instructions, orEmpty(work).withPurchaseItems(items));
    }

    /** Pay a bill or a fee and bring the receipt. {@code amount} is the bill you expect, in kobo. */
    public static Tasks.Stage payment(Tasks.Location location, String instructions, String payee, long amount, Work work) {
        return stage("PAYMENT", location, instructions, orEmpty(work).withPayee(payee).withAmount(amount));
    }

    /** Look at something and answer your questions. */
    public static Tasks.Stage inspection(Tasks.Location location, String instructions, List<Question> questions, Work work) {
        return stage("INSPECTION", location, instructions, orEmpty(work).withQuestions(questions));
    }

    /** Anything else, described in {@code instructions}. {@code effort} (S, M or L) sets the stage fee. */
    public static Tasks.Stage custom(Tasks.Location location, String instructions, String effort, Work work) {
        return stage("CUSTOM", location, instructions, orEmpty(work).withEffort(effort));
    }

    private static Work orEmpty(Work work) {
        return work != null ? work : work();
    }

    private static Tasks.Stage stage(String type, Tasks.Location location, String instructions, Work work) {
        return new Tasks.Stage(type, location, instructions, null, null, null, null, null, null).withWork(orEmpty(work));
    }

    // ── Stage fields ─────────────────────────────────────────────────────────

    /**
     * The errand fields of a stage, written and read at the stage's top level:
     * <ul>
     * <li>{@code reference}: your name for the stage, unique in the task; what a PURCHASE buys is carried under it</li>
     * <li>{@code expectedMinutes}: minutes the rider is expected on site (defaults by type)</li>
     * <li>{@code maxMinutes}: the most time on site that is paid for, up to 240</li>
     * <li>{@code openingHours}: when the place is open, in Lagos time</li>
     * <li>{@code items}: PURCHASE: the shopping list, sent and read as {@code items}</li>
     * <li>{@code ifUnavailable}: PURCHASE, default SKIP: SKIP, SIMILAR or ASK</li>
     * <li>{@code spendLimit}: PURCHASE and PAYMENT: the most the rider may spend here, in kobo</li>
     * <li>{@code payWith}: PURCHASE and PAYMENT, default MERCHANT_TRANSFER: MERCHANT_TRANSFER or PREPAID</li>
     * <li>{@code merchant}: the shop's account, if you know it</li>
     * <li>{@code payee}: PAYMENT: who is paid</li>
     * <li>{@code amount}: PAYMENT: the bill you expect, in kobo</li>
     * <li>{@code billReference}: PAYMENT: a bill or account number</li>
     * <li>{@code questions}: INSPECTION (required) and CUSTOM (optional)</li>
     * <li>{@code effort}: CUSTOM, required: S, M or L; it sets the stage fee</li>
     * <li>{@code simulate}: test keys only: what the simulated rider does here</li>
     * <li>{@code result}: response: what came back from the stage</li>
     * <li>{@code reason}: response: why it FAILED or was SKIPPED</li>
     * <li>{@code approval}: response: the stage's latest question from the rider</li>
     * <li>{@code plannedArrivalAt}: response: when the rider is expected here</li>
     * </ul>
     */
    public record Work(
            String reference,
            Integer expectedMinutes,
            Integer maxMinutes,
            OpeningHours openingHours,
            @JsonProperty("items") List<PurchaseItem> purchaseItems,
            String ifUnavailable,
            Long spendLimit,
            String payWith,
            Merchant merchant,
            String payee,
            Long amount,
            String billReference,
            List<Question> questions,
            String effort,
            Simulate simulate,
            StageResult result,
            String reason,
            Approval approval,
            String plannedArrivalAt) {
        /** The same fields with reference: your name for the stage, unique in the task; what a PURCHASE buys is carried under it. */
        public Work withReference(String value) {
            return new Work(value, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with expectedMinutes: minutes the rider is expected on site (defaults by type). */
        public Work withExpectedMinutes(Integer value) {
            return new Work(reference, value, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with maxMinutes: the most time on site that is paid for, up to 240. */
        public Work withMaxMinutes(Integer value) {
            return new Work(reference, expectedMinutes, value, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with openingHours: when the place is open, in Lagos time. */
        public Work withOpeningHours(OpeningHours value) {
            return new Work(reference, expectedMinutes, maxMinutes, value, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with purchaseItems: PURCHASE: the shopping list, sent and read as {@code items}. */
        public Work withPurchaseItems(List<PurchaseItem> value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, value, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with ifUnavailable: PURCHASE, default SKIP: SKIP, SIMILAR or ASK. */
        public Work withIfUnavailable(String value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, value, spendLimit, payWith, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with spendLimit: PURCHASE and PAYMENT: the most the rider may spend here, in kobo. */
        public Work withSpendLimit(Long value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, value, payWith, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with payWith: PURCHASE and PAYMENT, default MERCHANT_TRANSFER: MERCHANT_TRANSFER or PREPAID. */
        public Work withPayWith(String value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, value, merchant, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with merchant: the shop's account, if you know it. */
        public Work withMerchant(Merchant value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, value, payee, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with payee: PAYMENT: who is paid. */
        public Work withPayee(String value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, value, amount, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with amount: PAYMENT: the bill you expect, in kobo. */
        public Work withAmount(Long value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, value, billReference, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with billReference: PAYMENT: a bill or account number. */
        public Work withBillReference(String value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, value, questions, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with questions: INSPECTION (required) and CUSTOM (optional). */
        public Work withQuestions(List<Question> value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, value, effort, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with effort: CUSTOM, required: S, M or L; it sets the stage fee. */
        public Work withEffort(String value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, questions, value, simulate, result, reason, approval, plannedArrivalAt);
        }

        /** The same fields with simulate: test keys only: what the simulated rider does here. */
        public Work withSimulate(Simulate value) {
            return new Work(reference, expectedMinutes, maxMinutes, openingHours, purchaseItems, ifUnavailable, spendLimit, payWith, merchant, payee, amount, billReference, questions, effort, value, result, reason, approval, plannedArrivalAt);
        }

        boolean isEmpty() {
            return Mapper.PLAIN.valueToTree(this).isEmpty();
        }
    }

    /** One line of a PURCHASE's shopping list. {@code maxUnitPrice}: the most you pay for one; above it the rider asks you. */
    public record PurchaseItem(String name, Integer quantity, String unit, Long maxUnitPrice, String note, String ifUnavailable) {
        public PurchaseItem(String name, Integer quantity) {
            this(name, quantity, null, null, null, null);
        }
    }

    /** A question the rider answers at the stop. {@code type} is YES_NO, TEXT, NUMBER, PHOTO or CHOICE (with 2 to 10 options). */
    public record Question(String id, String type, String text, List<String> options, Boolean required) {
        public Question(String type, String text) {
            this(null, type, text, null, null);
        }
    }

    /** When a place is open, in Lagos time (HH:MM); null {@code days} means every day. */
    public record OpeningHours(List<String> days, String opens, String closes) {}

    /** The shop's account: the account the rider pays must match it exactly. */
    public record Merchant(String bankCode, String accountNumber) {}

    /** Test keys only. {@code outcome} is COMPLETE, FAIL, ASK or OVERRUN. */
    public record Simulate(String outcome, String reason, String kind, Long spend, Integer minutes) {
        public Simulate(String outcome) {
            this(outcome, null, null, null, null);
        }
    }

    /** What came back from a work stage. Open its files with {@link Tasks#viewFile}. */
    public record StageResult(
            Integer minutes,
            Integer paidMinutes,
            List<String> photos,
            String note,
            String receipt,
            List<PurchaseItemResult> items,
            Long spend,
            JsonNode answers) {}

    /** One shopping-list line as the rider found it. {@code status} is BOUGHT, SWAPPED or MISSING. */
    public record PurchaseItemResult(String name, int quantity, String status, Long unitPrice, Long total, String substitute) {}

    /** How long the rider waits for your answer (60 to 1,800 s) and what happens if nobody answers (DECLINE or APPROVE). */
    public record ApprovalsPolicy(Integer waitSeconds, String ifNoAnswer) {}

    // ── Responses ────────────────────────────────────────────────────────────

    /**
     * A question from the rider; answer it with {@link Tasks#decideApproval}. {@code kind} is
     * SUBSTITUTE, PRICE_ABOVE_MAX, OVER_SPEND_LIMIT, MORE_TIME or QUESTION; {@code status} is PENDING,
     * APPROVED, DECLINED or EXPIRED.
     */
    public record Approval(
            String id,
            String taskId,
            int stageIndex,
            String stageReference,
            String kind,
            String status,
            String question,
            JsonNode proposal,
            String photo,
            ApprovalEffect effect,
            String expiresAt,
            String ifNoAnswer,
            String decidedBy,
            String decidedAt,
            String note,
            String createdAt) {}

    /** What approving does to the price. */
    public record ApprovalEffect(boolean raisesMaximum, long spendChange, long maxTotalAfter) {}

    /** {@code decision} is APPROVE or DECLINE; {@code note} is shown to the rider and may be null. */
    public record ApprovalDecision(String decision, String note) {}

    public record ApprovalList(List<Approval> approvals) {}

    /** How far an errand got. {@code outcome} (DONE, PARTLY_DONE, NOT_DONE) is set when it ends. */
    public record Summary(String outcome, int completed, int failed, int skipped) {}

    public record Amounts(long expected, long max) {}

    /** An errand's quote: a range, split into the service and the goods. {@code total} is expected; {@code maxTotal} is held at create. */
    public record QuotePricing(String kind, long total, long maxTotal, long minTotal, Amounts service, Amounts spend, JsonNode lines) {}

    /** When the rider is expected at each stop, if a rider reaches the first at {@code startsAt}. */
    public record Schedule(String startsAt, String finishesAt, List<ScheduleStage> stages) {}

    public record ScheduleStage(int index, String arrivesAt, String leavesAt, String closesAt, Integer slackMinutes) {}

    /**
     * Something worth a look before you pay; warnings never block. Codes include TIGHT_OPENING_HOURS,
     * TIGHT_DELIVER_BY, SPEND_LIMIT_BELOW_LIST, NO_APPROVAL_LISTENER and LONG_WAIT_HOLD.
     */
    public record Warning(String code, Integer stageIndex, String message) {}

    /** The price list and limits in force for your account. {@code available} is false until errands are on for you. */
    public record Options(
            boolean available,
            String currency,
            JsonNode stageTypes,
            JsonNode paidWait,
            long onSitePerMinute,
            JsonNode handling,
            PayWith payWith,
            JsonNode limits,
            JsonNode approvals) {}

    public record PayWith(List<String> available, MerchantTransfer merchantTransfer) {}

    public record MerchantTransfer(boolean enabled, boolean paused) {}

    /** The task after a skip. */
    public record SkipResponse(Tasks.Task task) {}

    /** A link to a photo or receipt, valid for 15 minutes. */
    public record FileView(String downloadUrl, String expiresAt) {}

    // ── Stage JSON ───────────────────────────────────────────────────────────

    /** Reads and writes a stage's errand fields at its top level, and a PURCHASE's {@code items} as its shopping list. */
    static final class Mapper {
        @JsonSerialize(using = JsonSerializer.None.class)
        @JsonDeserialize(using = JsonDeserializer.None.class)
        @JsonIgnoreProperties({"work"})
        interface PlainStage {}

        static final ObjectMapper PLAIN = Relay.JSON.copy().addMixIn(Tasks.Stage.class, PlainStage.class);

        private Mapper() {}
    }

    static final class StageSerializer extends JsonSerializer<Tasks.Stage> {
        @Override
        public void serialize(Tasks.Stage stage, JsonGenerator gen, SerializerProvider provider) throws IOException {
            ObjectNode node = Mapper.PLAIN.valueToTree(stage);
            if (stage.work() != null) {
                ObjectNode work = Mapper.PLAIN.valueToTree(stage.work());
                work.properties().forEach(field -> node.set(field.getKey(), field.getValue()));
            }
            gen.writeTree(node);
        }
    }

    static final class StageDeserializer extends JsonDeserializer<Tasks.Stage> {
        @Override
        public Tasks.Stage deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = parser.readValueAsTree();
            boolean purchase = "PURCHASE".equals(node.path("type").asText());
            ObjectNode stagePart = node.deepCopy();
            ObjectNode workPart = node.deepCopy();
            if (purchase) stagePart.remove("items");
            else workPart.remove("items");
            Tasks.Stage stage = Mapper.PLAIN.treeToValue(stagePart, Tasks.Stage.class);
            Work work = Mapper.PLAIN.treeToValue(workPart, Work.class);
            return work.isEmpty() ? stage : stage.withWork(work);
        }
    }
}
