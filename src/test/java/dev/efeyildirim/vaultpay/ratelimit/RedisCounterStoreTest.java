package dev.efeyildirim.vaultpay.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.efeyildirim.vaultpay.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Needs a real Redis, so it only runs in the Testcontainers variant (-PtestDb=postgres, i.e. CI). */
@EnabledIfSystemProperty(named = "spring.profiles.active", matches = ".*test-postgres.*")
class RedisCounterStoreTest extends IntegrationTest {

    @Autowired
    CounterStore store;
    @Autowired
    StringRedisTemplate redis;

    @Test
    void usesRedisAndSetsTheExpiryOnTheFirstIncrement() {
        String key = "test:" + UUID.randomUUID();

        assertThat(store).isInstanceOf(RedisCounterStore.class);
        assertThat(store.increment(key, Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(store.increment(key, Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }

    @Test
    void concurrentIncrementsAreAllCounted() throws Exception {
        String key = "test:" + UUID.randomUUID();

        List<Future<Long>> results = runConcurrently(20, i -> () -> store.increment(key, Duration.ofMinutes(1)));

        assertThat(results).hasSize(20);
        assertThat(redis.opsForValue().get(key)).isEqualTo("20");
    }
}
