package com.weblogs.blog.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Thin, typed wrapper around {@link RedisTemplate} for application-level caching.
 *
 * <p>Key naming conventions:
 * <pre>
 *   post:list:{params-hash}     – paginated public post list
 *   post:slug:{slug}            – single post by slug
 *   tags:all                    – full tag list
 *   categories:all              – full category list
 *   post:list:keys              – SET tracking all active post-list cache keys
 *   view:post:{postId}          – Redis counter for view increments (String template)
 *   view:dirty:posts            – SET of postIds with pending view count updates
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    // Injected by name — Spring resolves the correct bean via qualifier
    private final RedisTemplate<String, Object> jsonRedisTemplate;

    // ── Key constants ──────────────────────────────────────────────────────────

    public static final String POST_SLUG_PREFIX       = "post:slug:";
    public static final String POST_LIST_PREFIX       = "post:list:";
    public static final String POST_LIST_KEYS_TRACKER = "post:list:keys";
    public static final String TAGS_ALL               = "tags:all";
    public static final String CATEGORIES_ALL         = "categories:all";
    public static final String TRENDING_POSTS         = "post:trending";
    public static final String FEATURED_POSTS         = "post:featured";
    public static final String ADMIN_STATS            = "admin:stats";

    // ── Generic get/put/evict ─────────────────────────────────────────────────

    /**
     * Retrieves a cached value. Returns {@link Optional#empty()} on miss or Redis error.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        try {
            Object value = jsonRedisTemplate.opsForValue().get(key);
            return Optional.ofNullable((T) value);
        } catch (Exception e) {
            log.warn("Cache GET failed for key '{}': {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores a value with the given TTL. Silently swallows Redis errors so the
     * application continues working without cache.
     */
    public void put(String key, Object value, Duration ttl) {
        try {
            jsonRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Cache PUT failed for key '{}': {}", key, e.getMessage());
        }
    }

    /** Deletes a single cache key. */
    public void evict(String key) {
        try {
            jsonRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Cache EVICT failed for key '{}': {}", key, e.getMessage());
        }
    }

    // ── Post list cache helpers ───────────────────────────────────────────────

    /**
     * Stores a post list response AND registers the key in the tracker SET so it
     * can be bulk-evicted on any post mutation.
     */
    public void putListCache(String key, Object value, Duration ttl) {
        put(key, value, ttl);
        try {
            jsonRedisTemplate.opsForSet().add(POST_LIST_KEYS_TRACKER, key);
            // Keep the tracker alive slightly longer than the longest list TTL
            jsonRedisTemplate.expire(POST_LIST_KEYS_TRACKER, ttl.plusSeconds(60));
        } catch (Exception e) {
            log.warn("Failed to register list cache key '{}' in tracker: {}", key, e.getMessage());
        }
    }

    /**
     * Evicts ALL post list caches in one pipeline call.
     * Called whenever a post is created, updated, published, unpublished, or deleted.
     *
     * <p>Members of the tracker SET are always Strings (they were added as Strings
     * via {@link #putListCache}). The {@code instanceof String s} guard defends
     * against the unlikely-but-catastrophic case of Redis data corruption or a
     * wrong deserializer producing a non-String value, which would otherwise throw
     * an unchecked {@code ClassCastException} inside the forEach lambda and be
     * silently swallowed by the outer catch block — leaving stale caches in place.
     */
    public void evictAllPostListCaches() {
        try {
            Set<Object> keys = jsonRedisTemplate.opsForSet().members(POST_LIST_KEYS_TRACKER);
            if (keys != null && !keys.isEmpty()) {
                int evicted = 0;
                for (Object k : keys) {
                    if (k instanceof String s) {
                        jsonRedisTemplate.delete(s);
                        evicted++;
                    } else {
                        // Should never happen — log so we know if the serializer misbehaves
                        log.warn("Unexpected non-String key in post list tracker (type={}): {}",
                                k == null ? "null" : k.getClass().getSimpleName(), k);
                    }
                }
                log.debug("Evicted {} post list cache entries", evicted);
            }
            jsonRedisTemplate.delete(POST_LIST_KEYS_TRACKER);
        } catch (Exception e) {
            log.warn("Failed to evict post list caches: {}", e.getMessage());
        }
    }
}
