package ng.com.sendrelay.server;

/** The request never got an answer: the connection failed or timed out. */
public class NetworkException extends RelayException {
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
