package com.weblogs.blog.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.weblogs.blog.exception.ForbiddenException;
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

    private static final long IMAGE_MAX_SIZE_BYTES = 5L  * 1024 * 1024;  // 5 MB
    private static final long VIDEO_MAX_SIZE_BYTES = 50L * 1024 * 1024;  // 50 MB

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    );

    private final Cloudinary cloudinary;

    /**
     * Validates and uploads an image file to Cloudinary.
     *
     * @param file the multipart file from the request
     * @return the secure Cloudinary URL of the uploaded image
     * @throws IllegalArgumentException if content-type or size validation fails
     * @throws RuntimeException         if the Cloudinary upload fails
     */
    public String upload(MultipartFile file) {
        validateFile(file, ALLOWED_IMAGE_TYPES, IMAGE_MAX_SIZE_BYTES, "JPEG, PNG, GIF, WebP, SVG");

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
        validateFile(file, ALLOWED_VIDEO_TYPES, VIDEO_MAX_SIZE_BYTES, "MP4, WebM, MOV, AVI");

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

    private void validateFile(MultipartFile file, List<String> allowedTypes,
                              long maxSizeBytes, String allowedTypesLabel) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid file type. Allowed types: " + allowedTypesLabel);
        }
        if (file.getSize() > maxSizeBytes) {
            long limitMb = maxSizeBytes / (1024 * 1024);
            throw new IllegalArgumentException(
                    "File size exceeds the maximum allowed limit of " + limitMb + " MB");
        }
    }
}
