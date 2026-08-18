package com.weblogs.blog.media;

import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.exception.RateLimitExceededException;
import com.weblogs.blog.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Media upload controller with per-user Redis rate limiting.
 *
 * <p>Each authenticated user is allowed at most {@value #UPLOAD_LIMIT} uploads
 * within a rolling {@value #WINDOW_MINUTES}-minute window.
 * The counter is stored in Redis with a TTL matching the window duration.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private static final int UPLOAD_LIMIT   = 20;   // max uploads per window
    private static final int WINDOW_MINUTES = 60;   // rolling window in minutes

    private final MediaService        mediaService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Uploads an image to Cloudinary and returns the secure URL.
     *
     * <p>Requires authentication (not in the public permitAll list).
     * Size limit is enforced by Spring multipart config (6 MB request max)
     * and additionally by {@link MediaService} (5 MB per file).
     * Uploads are rate-limited to {@value #UPLOAD_LIMIT} per user per hour.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        enforceUploadRateLimit(currentUser);

        String url = mediaService.upload(file);
        log.debug("User {} uploaded image: {}", currentUser.getId(), url);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    /**
     * Increments the per-user upload counter in Redis.
     * On first upload in the window, sets a TTL equal to the window duration.
     * Throws {@link RateLimitExceededException} if the limit is exceeded.
     */
    private void enforceUploadRateLimit(User user) {
        String key = "media:upload:ratelimit:" + user.getId();

        Long count = redisTemplate.opsForValue().increment(key);
        if (Objects.equals(count, 1L)) {
            // First upload in this window — set TTL
            redisTemplate.expire(key, Duration.ofMinutes(WINDOW_MINUTES));
        }

        if (count != null && count > UPLOAD_LIMIT) {
            log.warn("Upload rate limit exceeded for user {}: {} uploads in {}m window",
                    user.getId(), count, WINDOW_MINUTES);
            throw new RateLimitExceededException(
                    String.format("Upload limit of %d per hour exceeded. Try again later.", UPLOAD_LIMIT));
        }
    }
}
