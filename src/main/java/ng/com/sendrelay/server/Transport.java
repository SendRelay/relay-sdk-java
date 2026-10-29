package ng.com.sendrelay.server;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Sends one HTTP request. The default uses {@link java.net.http.HttpClient}; supply your own to add
 * a proxy, tracing or a test double. Throw {@link IOException} when there is no answer.
 */
@FunctionalInterface
public interface Transport {
    Response send(Request request) throws IOException, InterruptedException;

    record Request(String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) {}

    record Response(int status, byte[] body) {}
}
