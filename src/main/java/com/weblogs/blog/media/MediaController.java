package com.weblogs.blog.media;

import com.weblogs.blog.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Uploads an image to Cloudinary and returns the secure URL.
     *
     * <p>Requires authentication (not in the public permitAll list).
     * Size limit is enforced by Spring multipart config (6 MB request max)
     * and additionally by {@link MediaService} (5 MB per file).
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @RequestParam("file") MultipartFile file) {

        String url = mediaService.upload(file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }
}
