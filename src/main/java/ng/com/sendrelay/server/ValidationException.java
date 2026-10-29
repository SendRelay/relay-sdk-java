package ng.com.sendrelay.server;

/** The SDK rejected the input before sending anything. */
public class ValidationException extends RelayException {
    private final String field;

    public ValidationException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
