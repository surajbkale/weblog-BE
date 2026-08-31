package com.weblogs.blog.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.weblogs.blog.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private final Cloudinary     cloudinary;
    private final AppProperties  appProperties;  // single source of truth for size limits

    /**
     * Validates and uploads an image file to Cloudinary.
     *
     * @param file the multipart file from the request
     * @return the secure Cloudinary URL of the uploaded image
     * @throws IllegalArgumentException if content-type or size validation fails
     * @throws RuntimeException         if the Cloudinary upload fails
     */
    public String upload(MultipartFile file) {
        validateImage(file);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
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

    /**
     * Validates and uploads a video file to Cloudinary.
     *
     * @param file the multipart video file from the request
     * @return the secure Cloudinary URL of the uploaded video
     * @throws IllegalArgumentException if content-type or size validation fails
     * @throws RuntimeException         if the Cloudinary upload fails
     */
    public String uploadVideo(MultipartFile file) {
        validateVideo(file);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder",          "blog/videos",
                            "resource_type",   "video",
                            "use_filename",    true,
                            "unique_filename", true
                    )
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
