package ng.com.sendrelay.server;

/** Base class for every error this SDK throws. Unchecked, so it fits lambdas and Kotlin. */
public class RelayException extends RuntimeException {
    public RelayException(String message) {
        super(message);
    }

    public RelayException(String message, Throwable cause) {
        super(message, cause);
    }
}
