package com.weblogs.blog.auth;

import com.weblogs.blog.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based sliding-window rate limiter for the login endpoint.
 * Uses INCR + EXPIRE (set only on first increment) — no extra library needed.
 *
 * <p>Key format: {@code ratelimit:login:{identifier}}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:login:";

    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;

    /**
     * Returns {@code true} if the request is allowed, {@code false} if rate limited.
     *
     * @param identifier email or IP address (prefer email for login)
     */
    public boolean isAllowed(String identifier) {
        String key = KEY_PREFIX + identifier;
        int maxAttempts  = appProperties.getRateLimit().getLogin().getMaxAttempts();
        int windowSeconds = appProperties.getRateLimit().getLogin().getWindowSeconds();

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            log.warn("Redis unavailable — allowing request for key: {}", key);
            return true; // fail-open to avoid locking all users out on Redis downtime
        }

        if (count == 1L) {
            // First request in this window — set the TTL
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count > maxAttempts) {
            log.warn("Rate limit exceeded for key: {} (count={})", key, count);
            return false;
        }

        return true;
    }

    /** Returns the remaining TTL (seconds) for a given identifier, or 0 if not set. */
    public long getTtlSeconds(String identifier) {
        Long ttl = redisTemplate.getExpire(KEY_PREFIX + identifier, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
