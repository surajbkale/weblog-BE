package com.weblogs.blog.comment;

import com.weblogs.blog.comment.dto.CommentRequest;
import com.weblogs.blog.comment.dto.CommentResponse;
import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.common.PaginatedResponse;
import com.weblogs.blog.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // ── Add comment ───────────────────────────────────────────────────────────

    @PostMapping("/api/v1/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal User currentUser) {

        CommentResponse response = commentService.addComment(postId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // ── List comments for post (public) ───────────────────────────────────────

    @GetMapping("/api/v1/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentResponse>>> listComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.ok(commentService.listByPost(postId, pageable)));
    }

    // ── Edit comment ──────────────────────────────────────────────────────────

    @PutMapping("/api/v1/comments/{id}")
    public ResponseEntity<ApiResponse<CommentResponse>> editComment(
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(commentService.editComment(id, request, currentUser)));
    }

    // ── Delete comment ────────────────────────────────────────────────────────

    @DeleteMapping("/api/v1/comments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        commentService.deleteComment(id, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Comment deleted"));
    }

    // ── List replies to a comment (public) ───────────────────────────

    /**
     * GET /api/v1/comments/{id}/replies
     *
     * <p>Returns a paginated list of direct replies to the specified parent comment.
     * Public — no authentication required (ensure this route is whitelisted in SecurityConfig),
     * same as the flat post-comment list.
     * Soft-deleted replies are included so the frontend can render "[deleted]" stubs
     * and keep thread depth/ordering intact.
     */
    @GetMapping("/api/v1/comments/{id}/replies")
    public ResponseEntity<ApiResponse<PaginatedResponse<CommentResponse>>> listReplies(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.ok(commentService.listReplies(id, pageable)));
    }
}
