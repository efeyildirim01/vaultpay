package dev.efeyildirim.vaultpay.ratelimit;

import java.time.Duration;

/** Where rate-limit counters live. The increment must be atomic: concurrent requests must never share a count. */
public interface CounterStore {

    /** Adds one to the counter and returns the new value. A new counter expires after {@code ttl}. */
    long increment(String key, Duration ttl);
}
