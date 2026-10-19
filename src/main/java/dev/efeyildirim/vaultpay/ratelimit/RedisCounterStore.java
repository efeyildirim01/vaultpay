package dev.efeyildirim.vaultpay.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Counters shared by every application instance. INCR and the expiry run in one Lua script, which Redis
 * executes atomically: with two separate commands a crash in between would leave a counter that never
 * expires, and that user would stay limited forever.
 */
@Component
@ConditionalOnProperty(name = "vaultpay.rate-limit.store", havingValue = "redis")
public class RedisCounterStore implements CounterStore {

    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count""", Long.class);

    private final StringRedisTemplate redis;

    public RedisCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long count = redis.execute(INCREMENT, List.of(key), Long.toString(ttl.toMillis()));
        return count == null ? 0 : count;
    }
}
