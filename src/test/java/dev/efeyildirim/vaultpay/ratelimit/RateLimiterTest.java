package dev.efeyildirim.vaultpay.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final Duration MINUTE = Duration.ofMinutes(1);

    // 10 seconds into a minute, so the window ends 50 seconds from now.
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T12:00:10Z"));
    private final RateLimiter limiter = new RateLimiter(new InMemoryCounterStore(clock), clock);

    @Test
    void allowsUpToTheLimitThenRejectsWithTimeUntilTheWindowEnds() {
        for (int i = 1; i <= 3; i++) {
            assertThat(limiter.tryAcquire("user-a", 3, MINUTE).allowed()).isTrue();
        }

        RateLimiter.Decision fourth = limiter.tryAcquire("user-a", 3, MINUTE);

        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.retryAfter()).isEqualTo(Duration.ofSeconds(50));
    }

    @Test
    void aNewWindowStartsFromZero() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("user-a", 3, MINUTE);
        }

        clock.advance(Duration.ofSeconds(50)); // 12:01:00, next window

        assertThat(limiter.tryAcquire("user-a", 3, MINUTE).allowed()).isTrue();
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("user-a", 3, MINUTE);
        }

        assertThat(limiter.tryAcquire("user-a", 3, MINUTE).allowed()).isFalse();
        assertThat(limiter.tryAcquire("user-b", 3, MINUTE).allowed()).isTrue();
    }

    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        assertThat(new RateLimitExceededException(Duration.ofMillis(1)).retryAfterSeconds()).isEqualTo(1);
        assertThat(new RateLimitExceededException(Duration.ofMillis(1_001)).retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    void inMemoryCounterLosesNoIncrementsUnderConcurrency() throws InterruptedException {
        InMemoryCounterStore store = new InMemoryCounterStore(clock);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for (int i = 0; i < 1_000; i++) {
            pool.submit(() -> store.increment("hot-key", MINUTE));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(store.increment("hot-key", MINUTE)).isEqualTo(1_001);
    }
}
