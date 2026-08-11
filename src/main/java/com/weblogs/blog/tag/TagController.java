package com.weblogs.blog.tag;

import com.weblogs.blog.tag.dto.TagResponse;
import com.weblogs.blog.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}

