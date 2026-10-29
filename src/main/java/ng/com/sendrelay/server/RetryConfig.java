package ng.com.sendrelay.server;

import java.time.Duration;
import java.util.List;

/**
 * How failed requests are retried: timeouts, network failures, and 408, 429 and 5xx responses.
 * Test-mode quota errors are never retried.
 */
public record RetryConfig(
        boolean enabled,
        int maxRetries,
        Duration initialDelay,
        Duration maxDelay,
        double backoffMultiplier,
        List<Integer> retryableStatusCodes) {

    public static RetryConfig defaults() {
        return new RetryConfig(true, 3, Duration.ofMillis(500), Duration.ofSeconds(10), 2, List.of(408, 429, 500, 502, 503, 504));
    }

    public static RetryConfig disabled() {
        return new RetryConfig(false, 0, Duration.ZERO, Duration.ZERO, 1, List.of());
    }

    public RetryConfig withMaxRetries(int n) {
        return new RetryConfig(enabled, n, initialDelay, maxDelay, backoffMultiplier, retryableStatusCodes);
    }

    long delayMillis(int attempt) {
        double base = Math.min(initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt), maxDelay.toMillis());
        // ±25% jitter so many clients don't retry in lockstep.
        return Math.max(0, Math.round(base + base * 0.25 * (Math.random() * 2 - 1)));
    }
}
