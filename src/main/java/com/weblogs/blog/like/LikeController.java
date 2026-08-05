package com.weblogs.blog.like;

import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts/{postId}/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> like(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User currentUser) {

        likeService.likePost(postId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Post liked"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unlike(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User currentUser) {

        likeService.unlikePost(postId, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Post unliked"));
    }
}
