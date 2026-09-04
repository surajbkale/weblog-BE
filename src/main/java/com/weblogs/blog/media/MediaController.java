package com.weblogs.blog.media;

import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.exception.RateLimitExceededException;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Media upload/delete controller with per-user Redis rate limiting.
 *
 * <p>Each authenticated user is allowed at most {@value #UPLOAD_LIMIT} uploads
 * within a rolling {@value #WINDOW_MINUTES}-minute window. The counter covers
 * both image and video uploads combined.
 *
 * <h3>Ownership model</h3>
 * <p>Assets are stored under {@code blog/users/{userId}/...} in Cloudinary.
 * The folder path encodes ownership, so no additional DB table is required.
 * On DELETE, the public_id path is verified against the caller's user ID.
 * Admins can delete any asset regardless of path.
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

    // ── Image upload ──────────────────────────────────────────────────────────

    /**
     * Uploads an image to Cloudinary and returns the secure URL.
     *
     * <p>Requires authentication. Size limit: 5 MB. Allowed types: JPEG, PNG, GIF, WebP, SVG.
     * Uploads are rate-limited to {@value #UPLOAD_LIMIT} per user per hour.
     * The file is stored under {@code blog/users/{userId}/} for ownership tracking.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        enforceUploadRateLimit(currentUser);

        String url = mediaService.upload(file, currentUser.getId());
        log.debug("User {} uploaded image: {}", currentUser.getId(), url);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    // ── Video upload ──────────────────────────────────────────────────────────

    /**
     * Uploads a video file to Cloudinary and returns the secure URL.
     *
     * <p>Requires authentication. Size limit: 50 MB. Allowed types: MP4, WebM, MOV, AVI.
     * Uploads share the same rate limit as image uploads ({@value #UPLOAD_LIMIT} per hour).
     * The file is stored under {@code blog/users/{userId}/videos/} for ownership tracking.
     */
    @PostMapping(value = "/upload/video", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        enforceUploadRateLimit(currentUser);

        String url = mediaService.uploadVideo(file, currentUser.getId());
        log.debug("User {} uploaded video: {}", currentUser.getId(), url);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a media asset from Cloudinary by its Cloudinary public ID.
     *
     * <p>Ownership is verified via the folder path convention:
     * assets uploaded by a user live under {@code blog/users/{userId}/...}.
     * If the {@code publicId} does not start with the caller's user prefix,
     * the request is rejected with 403. Admins can delete any asset.
     *
     * <p>The operation is idempotent: if the asset no longer exists in Cloudinary,
     * the endpoint returns 200 rather than 404, since the end state (asset gone) is achieved.
     *
     * @param publicId the Cloudinary public ID of the asset to delete
     *                 (e.g. {@code blog/users/abc123/my-photo})
     */
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestParam("publicId") String publicId,
            @AuthenticationPrincipal User currentUser) {

        boolean isAdmin = Role.ADMIN.equals(currentUser.getRole());
        mediaService.delete(publicId, currentUser.getId(), isAdmin);
        log.debug("User {} deleted media: publicId={}", currentUser.getId(), publicId);
        return ResponseEntity.ok(ApiResponse.ok(null));
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
