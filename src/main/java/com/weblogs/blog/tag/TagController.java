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

    private final TagRepository tagRepository;

    /** Public — no auth required. Returns all tags ordered by name. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> listAll() {
        List<TagResponse> tags = tagRepository.findAll().stream()
                .map(TagResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(tags));
    }
}
