package com.weblogs.blog.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.weblogs.blog.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

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

    private final Cloudinary     cloudinary;
    private final AppProperties  appProperties;  // single source of truth for size limits

    // ── Image upload ──────────────────────────────────────────────────────────

    /**
     * Validates and uploads an image file to Cloudinary.
     *
     * <p>The file is streamed via {@link MultipartFile#getInputStream()} so that
     * no full in-memory {@code byte[]} copy is needed — the data flows directly
     * from the multipart request buffer to the Cloudinary HTTP client.
     *
     * @param file the multipart file from the request
     * @return the secure Cloudinary URL of the uploaded image
     * @throws IllegalArgumentException if content-type or size validation fails
     * @throws RuntimeException         if the Cloudinary upload fails
     */
    public String upload(MultipartFile file) {
        validateImage(file);

        try (InputStream in = file.getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    in,
                    ObjectUtils.asMap(
                            "folder",          "blog",
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
     * so that at most one chunk (20 MB) is ever resident in heap at a time,
     * regardless of the total video size. This avoids the {@code byte[]} heap
     * allocation that {@code file.getBytes()} would require for a 50 MB upload.
     *
     * @param file the multipart video file from the request
     * @return the secure Cloudinary URL of the uploaded video
     * @throws IllegalArgumentException if content-type or size validation fails
     * @throws RuntimeException         if the Cloudinary upload fails
     */
    public String uploadVideo(MultipartFile file) {
        validateVideo(file);

        try (InputStream in = file.getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().uploadLarge(
                    in,
                    ObjectUtils.asMap(
                            "folder",          "blog/videos",
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
