package com.weblogs.blog.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.weblogs.blog.config.AppProperties;
import com.weblogs.blog.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );

    /**
     * Accepted video file extensions (lower-cased). Used as a fallback when the
     * browser sends a generic content-type such as {@code application/octet-stream}.
     */
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = List.of(
            ".mp4", ".webm", ".mov", ".avi", ".mkv", ".m4v"
    );

    /**
     * Cloudinary chunked-upload chunk size for videos: 20 MB.
     *
     * <p>Cloudinary's {@code uploadLarge} splits the stream into chunks of this size
     * and uploads them sequentially. Only one chunk at a time is held in memory, so
     * a 50 MB video requires at most ~20 MB of heap regardless of file size.
     * The minimum chunk size allowed by Cloudinary is 5 MB; 20 MB is a safe default
     * that keeps the number of HTTP round-trips low for typical blog videos.
     */
    private static final int VIDEO_CHUNK_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB

    /**
     * Cloudinary folder prefix for all user-scoped uploads.
     *
     * <p>Images are stored under {@code blog/users/{userId}/} and videos under
     * {@code blog/users/{userId}/videos/}. Embedding the user ID in the folder path
     * is the ownership verification mechanism for the delete endpoint — no DB table
     * is required. An upload by user A will have a public_id that starts with
     * {@code blog/users/{userA-uuid}/}, so user B cannot delete it by constructing
     * an arbitrary public_id.
     */
    private static final String BASE_FOLDER = "blog/users/";

    private final Cloudinary    cloudinary;
    private final AppProperties appProperties;

    // ── Image upload ──────────────────────────────────────────────────────────

    /**
     * Validates and uploads an image file to Cloudinary.
     *
     * <p>The file is streamed via {@link MultipartFile#getInputStream()} so that
     * no full in-memory {@code byte[]} copy is needed — the data flows directly
     * from the multipart request buffer to the Cloudinary HTTP client.
     *
     * <p>Files are stored under {@code blog/users/{userId}/} — see {@link #BASE_FOLDER}.
     *
     * @param file   the multipart file from the request
     * @param userId the authenticated user's ID (used to scope the Cloudinary folder)
     * @return the secure Cloudinary URL of the uploaded image
     */
    public String upload(MultipartFile file, UUID userId) {
        validateImage(file);
        String folder = BASE_FOLDER + userId;

        try (InputStream in = file.getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    in,
                    ObjectUtils.asMap(
                            "folder",          folder,
                            "resource_type",   "image",
                            "use_filename",    true,
                            "unique_filename", true
                    )
            );
            String url = (String) result.get("secure_url");
            log.debug("Cloudinary image upload succeeded: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary image upload failed", e);
            throw new RuntimeException("Image upload failed. Please try again later.");
        }
    }

    // ── Video upload ──────────────────────────────────────────────────────────

    /**
     * Validates and uploads a video file to Cloudinary using chunked upload.
     *
     * <p>Uses {@code uploadLarge} with {@link #VIDEO_CHUNK_SIZE_BYTES}-sized chunks
     * so that at most one chunk (20 MB) is ever resident in heap at a time.
     *
     * <p>Files are stored under {@code blog/users/{userId}/videos/}.
     *
     * @param file   the multipart video file from the request
     * @param userId the authenticated user's ID (used to scope the Cloudinary folder)
     * @return the secure Cloudinary URL of the uploaded video
     */
    public String uploadVideo(MultipartFile file, UUID userId) {
        validateVideo(file);
        String folder = BASE_FOLDER + userId + "/videos";

        try (InputStream in = file.getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().uploadLarge(
                    in,
                    ObjectUtils.asMap(
                            "folder",          folder,
                            "resource_type",   "video",
                            "use_filename",    true,
                            "unique_filename", true
                    ),
                    VIDEO_CHUNK_SIZE_BYTES
            );
            String url = (String) result.get("secure_url");
            log.debug("Cloudinary video upload succeeded: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary video upload failed", e);
            throw new RuntimeException("Video upload failed. Please try again later.");
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a media asset from Cloudinary by its public ID.
     *
     * <h3>Ownership verification</h3>
     * <p>All assets uploaded via this service are stored under
     * {@code blog/users/{userId}/...} (see {@link #BASE_FOLDER}). On delete, we
     * verify that the {@code publicId} path prefix matches the requesting user's ID.
     * This is a zero-DB ownership check: the folder path <em>is</em> the ownership
     * record. Admins ({@code isAdmin = true}) bypass this check and can delete any asset.
     *
     * <h3>Resource type detection</h3>
     * <p>Cloudinary requires the correct {@code resource_type} to locate an asset.
     * We infer it from the public_id path: paths containing {@code /videos/} are
     * treated as {@code "video"}, everything else as {@code "image"}.
     *
     * <h3>Legacy assets</h3>
     * <p>Assets uploaded before this change live under {@code blog/} (not
     * {@code blog/users/{userId}/}) and fail the ownership prefix check. They can
     * only be deleted by an admin.
     *
     * @param publicId the Cloudinary public ID (e.g. {@code blog/users/abc123/my-photo})
     * @param userId   the requesting user's UUID
     * @param isAdmin  whether the caller has the ADMIN role
     * @throws ForbiddenException if the caller does not own the asset
     * @throws RuntimeException   if the Cloudinary destroy call fails
     */
    public void delete(String publicId, UUID userId, boolean isAdmin) {
        if (!isAdmin) {
            // The owner prefix for this user is "blog/users/{userId}"
            String ownerPrefix = BASE_FOLDER + userId;
            if (!publicId.startsWith(ownerPrefix + "/") && !publicId.equals(ownerPrefix)) {
                log.warn("Unauthorized delete attempt: userId={} tried to delete publicId={}",
                        userId, publicId);
                throw new ForbiddenException("You do not have permission to delete this asset.");
            }
        }

        // Infer resource type from path — video assets live under .../videos/...
        String resourceType = publicId.contains("/videos/") ? "video" : "image";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", resourceType)
            );
            String outcome = (String) result.get("result");
            if ("not found".equals(outcome)) {
                log.warn("Cloudinary delete: asset not found for publicId={}", publicId);
                // Not throwing — idempotent: if it's already gone, the goal is achieved
            } else {
                log.debug("Cloudinary delete succeeded: publicId={} result={}", publicId, outcome);
            }
        } catch (IOException e) {
            log.error("Cloudinary delete failed for publicId={}", publicId, e);
            throw new RuntimeException("Media deletion failed. Please try again later.");
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid file type. Allowed image types: JPEG, PNG, GIF, WebP, SVG");
        }
        long limitBytes = appProperties.getMedia().getImageMaxSizeBytes();
        if (file.getSize() > limitBytes) {
            long limitMb = limitBytes / (1024 * 1024);
            throw new IllegalArgumentException(
                    "Image size exceeds the maximum allowed limit of " + limitMb + " MB");
        }
    }

    /**
     * Video-specific validation. Browsers and operating systems are inconsistent
     * about the MIME type they report for video files:
     *   - Some send  video/mp4;codecs="avc1.42E01E"  (with codec params)
     *   - Some send  application/octet-stream  for any binary file
     *   - QuickTime (.mov) may arrive as video/quicktime or application/octet-stream
     *
     * Strategy:
     *   1. Accept anything whose MIME type starts with "video/" (covers all
     *      standard video/* subtypes and any ;params suffix).
     *   2. Accept "application/octet-stream" when the file extension is a
     *      recognised video extension (fallback for OS-level generic type).
     *   3. Reject everything else.
     */
    private void validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }

        String contentType = file.getContentType();
        String filename    = file.getOriginalFilename() != null
                            ? file.getOriginalFilename().toLowerCase() : "";

        boolean validMime = contentType != null && contentType.startsWith("video/");

        boolean genericMimeWithValidExt =
                "application/octet-stream".equals(contentType) &&
                ALLOWED_VIDEO_EXTENSIONS.stream().anyMatch(filename::endsWith);

        if (!validMime && !genericMimeWithValidExt) {
            throw new IllegalArgumentException(
                    "Invalid file type. Allowed video formats: MP4, WebM, MOV, AVI, MKV");
        }

        long limitBytes = appProperties.getMedia().getVideoMaxSizeBytes();
        if (file.getSize() > limitBytes) {
            long limitMb = limitBytes / (1024 * 1024);
            throw new IllegalArgumentException(
                    "Video size exceeds the maximum allowed limit of " + limitMb + " MB");
        }
    }
}
