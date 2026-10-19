package dev.efeyildirim.vaultpay.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter: time is cut into windows (e.g. whole minutes) and each key may make
 * {@code limit} requests per window. The counter key contains the window number, so a new window
 * starts from zero by itself and old counters simply expire.
 *
 * <p>Known trade-off: a client can send {@code limit} requests at the end of one window and {@code limit}
 * more at the start of the next, so the worst-case burst is twice the limit. A sliding window avoids that
 * at the cost of more state per key; for this simulator the simpler model is enough.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final CounterStore store;
    private final Clock clock;

    public RateLimiter(CounterStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** @param retryAfter time until the current window ends; only meaningful when not allowed */
    public record Decision(boolean allowed, long count, Duration retryAfter) {
    }

    public Decision tryAcquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        long windowMillis = window.toMillis();
        long windowNumber = now.toEpochMilli() / windowMillis;
        Duration untilNextWindow = Duration.ofMillis((windowNumber + 1) * windowMillis - now.toEpochMilli());
        long count;
        try {
            count = store.increment("rl:" + key + ":" + windowNumber, window);
        } catch (DataAccessException e) {
            // Fail open: if Redis is down, serving customers matters more than limiting them.
            // Money movement stays safe either way; the limiter is protection, not correctness.
            log.warn("Rate limiter unavailable, allowing request for {}", key, e);
            return new Decision(true, 0, Duration.ZERO);
        }
        return new Decision(count <= limit, count, untilNextWindow);
    }
}
