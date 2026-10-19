package dev.efeyildirim.vaultpay.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many requests, try again later");
        this.retryAfter = retryAfter;
    }

    /** Whole seconds, rounded up, for the Retry-After header. */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
