package ng.com.sendrelay.server;

import java.util.List;

/**
 * Insurance claims on a task, for insured goods lost, stolen or damaged after the rider collected them.
 * Amounts are in kobo; dates are Lagos days ({@code YYYY-MM-DD}). Test keys insure nothing, so their
 * tasks can't be claimed on.
 */
public final class Claims {
    private final Relay relay;

    Claims(Relay relay) {
        this.relay = relay;
    }

    private static String path(String taskId) {
        return "/sdk/tasks/" + Relay.pathId("taskId", taskId) + "/claim";
    }

    /** The task's claim, or whether you can file one and until when. */
    public State get(String taskId) {
        return relay.get(path(taskId), null, State.class);
    }

    /**
     * Somewhere to upload one photo or PDF for a claim. PUT the file to {@code uploadUrl} with the same
     * {@code Content-Type}, then pass {@code new File(upload.key(), name)} in a claim's files.
     */
    public Upload createUpload(String taskId, CreateUploadRequest request) {
        return relay.post(path(taskId) + "/evidence", request, null, Upload.class);
    }

    /** File a claim: one per task, once it has ended, for up to its insured value. */
    public Claim file(String taskId, FileRequest request) {
        return relay.post(path(taskId), request, null, Claim.class);
    }

    /** Answer SendRelay's request for more information, while the claim is {@code NEEDS_INFO}. */
    public Claim respond(String taskId, RespondRequest request) {
        return relay.post(path(taskId) + "/respond", request, null, Claim.class);
    }

    /** Withdraw a claim that is still being decided. You can file again afterwards. */
    public Claim withdraw(String taskId) {
        return relay.post(path(taskId) + "/withdraw", null, null, Claim.class);
    }

    // ── Requests ─────────────────────────────────────────────────────────────

    /** A file from {@link #createUpload}, by its key, with the name to show for it. */
    public record File(String key, String name) {}

    /**
     * {@code incidentType} is LOST, STOLEN or DAMAGED; {@code incidentDate} is {@code YYYY-MM-DD};
     * {@code claimedAmount} is in kobo. {@code files} may be null.
     */
    public record FileRequest(String incidentType, String incidentDate, long claimedAmount, String description, List<File> files) {}

    public record RespondRequest(String message, List<File> files) {}

    /** {@code contentType} is image/jpeg, image/png, image/webp or application/pdf. */
    public record CreateUploadRequest(String contentType) {}

    // ── Responses ────────────────────────────────────────────────────────────

    public record FileName(String name) {}

    /**
     * A claim. {@code status} is SUBMITTED, NEEDS_INFO (answer with {@link #respond}), WITH_INSURER,
     * APPROVED, DECLINED, PAID or WITHDRAWN.
     */
    public record Claim(
            String id,
            String reference,
            String status,
            String incidentType,
            String incidentDate,
            long claimedAmount,
            Long approvedAmount,
            String description,
            List<FileName> files,
            String infoRequest,
            String decisionReason,
            String filedOn,
            String decidedOn,
            String paidOn) {}

    /** {@code reason} says why not, when {@code canFile} is false and there is no claim. */
    public record State(
            Claim claim, boolean canFile, String reason, Long insuredValue, String certificateNumber, String closesOn) {}

    /** PUT the file to {@code uploadUrl} with {@code Content-Type: contentType} before {@code expiresAt}. */
    public record Upload(String uploadUrl, String key, String contentType, long maxFileSize, String expiresAt) {}
}
