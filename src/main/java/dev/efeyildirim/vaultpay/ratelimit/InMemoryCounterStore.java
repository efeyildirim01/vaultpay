package dev.efeyildirim.vaultpay.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Counters in this JVM only. Correct for a single instance (local dev, H2 tests); with several instances
 * each would count separately, which is why production uses {@link RedisCounterStore}.
 */
@Component
@ConditionalOnProperty(name = "vaultpay.rate-limit.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryCounterStore implements CounterStore {

    private static final int SWEEP_EVERY = 1_000;

    private record Counter(AtomicLong value, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicLong increments = new AtomicLong();
    private final Clock clock;

    public InMemoryCounterStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Instant now = clock.instant();
        if (increments.incrementAndGet() % SWEEP_EVERY == 0) {
            counters.values().removeIf(c -> !c.expiresAt().isAfter(now));
        }
        // compute() runs atomically per key, so two threads cannot both create or both miss an increment.
        Counter counter = counters.compute(key, (k, existing) ->
                existing == null || !existing.expiresAt().isAfter(now)
                        ? new Counter(new AtomicLong(), now.plus(ttl))
                        : existing);
        return counter.value().incrementAndGet();
    }
}
