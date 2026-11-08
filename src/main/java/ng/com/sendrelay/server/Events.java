package ng.com.sendrelay.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** The event log: every event about your tasks and payments, kept for 30 days. */
public final class Events {
    private final Relay relay;

    Events(Relay relay) {
        this.relay = relay;
    }

    /** One page of events for your account and the key's mode, newest first by default. {@code params} may be null. */
    public ListResponse list(ListParams params) {
        return relay.get("/sdk/events", params == null ? null : params.toQuery(), ListResponse.class);
    }

    /** Every matching event, fetched page by page as you iterate. */
    public Iterable<Event> listAll(ListParams params) {
        ListParams first = params == null ? new ListParams(null, null, null, null) : params;
        return () -> new Iterator<>() {
            private Iterator<Event> page = List.<Event>of().iterator();
            private String cursor = first.cursor();
            private boolean more = true;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && more) {
                    ListResponse response = list(new ListParams(first.limit(), first.order(), cursor, first.types()));
                    page = response.data().iterator();
                    cursor = response.cursor();
                    more = response.hasMore() && cursor != null && !cursor.isEmpty();
                }
                return page.hasNext();
            }

            @Override
            public Event next() {
                if (!hasNext()) throw new NoSuchElementException();
                return page.next();
            }
        };
    }

    /**
     * Filters for {@link #list}. Any may be null. {@code order} is "asc" (catch up, oldest first) or
     * "desc" (default); {@code types} limits the list to these webhook event types.
     */
    public record ListParams(Integer limit, String order, String cursor, List<String> types) {
        Map<String, Object> toQuery() {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("limit", limit);
            query.put("order", order);
            query.put("cursor", cursor);
            query.put("types", types == null || types.isEmpty() ? null : String.join(",", types));
            return query;
        }
    }

    /** Who and what an event is about. {@code taskId} and {@code riderId} may be null. */
    public record Metadata(String developerId, String taskId, String riderId, String source, String mode) {}

    /** One event exactly as its webhook carried it. Task events carry {@code data.revision}. */
    public record Event(String id, String type, String version, String timestamp, JsonNode data, Metadata metadata) {}

    public record ListResponse(List<Event> data, boolean hasMore, String cursor) {}
}
