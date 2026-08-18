package com.weblogs.blog.tag;

import com.weblogs.blog.tag.dto.TagResponse;
import com.weblogs.blog.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** Public — no auth required. Returns all tags, Redis-cached for 1 hr. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(tagService.listAll()));
    }

    /**
     * ADMIN only — permanently deletes a tag by ID.
     * Posts that reference this tag will have it removed automatically (DB CASCADE).
     * The tag list cache is evicted immediately.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        tagService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
