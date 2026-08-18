package com.weblogs.blog.post;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Manages post view counts via a Redis counter + periodic DB flush.
 *
 * <p>Flow:
 * <ol>
 *   <li>On each {@code GET /posts/{slug}}, call {@link #increment(UUID)} — this does a
 *       Redis {@code INCR} (atomic, ~1 ms) instead of a DB write.</li>
 *   <li>Every {@code app.cache.view-flush-interval-ms} (default 5 min), the scheduler
 *       reads all dirty counters, applies them to the DB with an additive UPDATE, and
 *       clears the Redis state.</li>
 * </ol>
 *
 * <p>Dirty tracking: a Redis SET {@code view:dirty:posts} holds postIds that have
 * unflused increments. This avoids a full {@code SCAN} over all Redis keys on every
 * flush cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountService {

    static final String VIEW_PREFIX    = "view:post:";
    static final String DIRTY_SET_KEY  = "view:dirty:posts";

    // String template — view counters are plain numbers, no JSON needed
    private final RedisTemplate<String, String> redisTemplate;
    private final PostRepository postRepository;

    // ── Increment (called on every public post read) ──────────────────────────

    /**
     * Atomically increments the in-memory view counter for the given post.
     * Never throws — Redis errors are logged and silently ignored.
     */
    public void increment(UUID postId) {
        try {
            String counterKey = VIEW_PREFIX + postId;
            redisTemplate.opsForValue().increment(counterKey);
            redisTemplate.opsForSet().add(DIRTY_SET_KEY, postId.toString());
        } catch (Exception e) {
            log.warn("View count increment failed for postId={}: {}", postId, e.getMessage());
        }
    }

    // ── Periodic flush ────────────────────────────────────────────────────────

    /**
     * Reads all dirty view counters from Redis, applies them to Postgres with an
     * additive UPDATE (never overwrites), then removes each postId from the dirty
     * set only after its flush succeeds.
     *
     * <p><b>Bug fix (previously):</b> The dirty set used to be deleted in a single
     * blanket call at the end of the loop. If any individual post flush failed
     * (e.g. post was hard-deleted, DB transient error), those postIds were silently
     * removed from the dirty set and their view counts were permanently lost.
     *
     * <p>Now each postId is removed from {@code view:dirty:posts} individually and
     * only on success. Failed postIds remain in the set and are retried on the next
     * scheduler cycle.
     *
     * <p>Scheduled with a fixed delay (not rate) so flushes never overlap.
     * Initial delay = flush interval to avoid an empty flush on cold start.
     */
    @Scheduled(fixedDelayString  = "${app.cache.view-flush-interval-ms:300000}",
               initialDelayString = "${app.cache.view-flush-interval-ms:300000}")
    @Transactional
    public void flushViewCounts() {
        Set<String> postIds;
        try {
            postIds = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
        } catch (Exception e) {
            log.error("Failed to read view:dirty:posts from Redis: {}", e.getMessage());
            return;
        }

        if (postIds == null || postIds.isEmpty()) {
            return;
        }

        log.debug("Flushing view counts for {} posts", postIds.size());
        int flushed  = 0;
        int skipped  = 0;
        int failed   = 0;

        for (String postIdStr : postIds) {
            String counterKey = VIEW_PREFIX + postIdStr;
            try {
                String raw = redisTemplate.opsForValue().get(counterKey);
                if (raw == null) {
                    // Counter already gone (e.g. evicted) — just clean up dirty-set entry
                    redisTemplate.opsForSet().remove(DIRTY_SET_KEY, postIdStr);
                    skipped++;
                    continue;
                }

                long delta = Long.parseLong(raw);
                if (delta <= 0) {
                    redisTemplate.opsForSet().remove(DIRTY_SET_KEY, postIdStr);
                    skipped++;
                    continue;
                }

                postRepository.incrementViewCount(UUID.fromString(postIdStr), delta);

                // SUCCESS — remove from dirty set and delete counter key.
                // Order matters: delete the counter BEFORE removing from the dirty set
                // so that a crash between these two lines leaves the entry in the set
                // (safe retry) rather than losing the counter reference.
                redisTemplate.delete(counterKey);
                redisTemplate.opsForSet().remove(DIRTY_SET_KEY, postIdStr);
                flushed++;

            } catch (Exception e) {
                // Leave this postId in the dirty set — it will be retried next cycle.
                // This is the critical difference from the old behaviour: we do NOT
                // delete DIRTY_SET_KEY at the end, so failed entries are preserved.
                failed++;
                log.warn("Failed to flush view count for postId={}: {}", postIdStr, e.getMessage());
            }
        }

        log.info("View count flush complete: {} flushed, {} skipped, {} failed (will retry)",
                flushed, skipped, failed);
    }
}
