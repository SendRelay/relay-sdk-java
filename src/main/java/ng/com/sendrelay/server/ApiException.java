package ng.com.sendrelay.server;

import com.fasterxml.jackson.databind.JsonNode;

/** The API answered with an error status. {@link #code()} is the API's code, such as NOT_FOUND or CONFLICT. */
public class ApiException extends RelayException {
    private final int statusCode;
    private final String code;
    private final JsonNode details;

    public ApiException(int statusCode, String code, String message, JsonNode details) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.details = details;
    }

    public int statusCode() {
        return statusCode;
    }

    public String code() {
        return code;
    }

    /** Anything extra the API sent, such as validation problems. Null when there was none. */
    public JsonNode details() {
        return details;
    }

    @Override
    public String toString() {
        return "ApiException(" + statusCode + " " + code + ": " + getMessage() + ")";
    }
}
