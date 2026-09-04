package com.weblogs.blog.post;

import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.common.PaginatedResponse;
import com.weblogs.blog.post.dto.CreatePostRequest;
import com.weblogs.blog.post.dto.PostListItemResponse;
import com.weblogs.blog.post.dto.PostResponse;
import com.weblogs.blog.post.dto.UpdatePostRequest;
import com.weblogs.blog.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal User currentUser) {

        PostResponse response = postService.createPost(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePostRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(postService.updatePost(id, request, currentUser)));
    }

    // ── Publish / Unpublish ───────────────────────────────────────────────────

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<PostResponse>> publish(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(postService.publishPost(id, currentUser)));
    }

    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<ApiResponse<PostResponse>> unpublish(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(postService.unpublishPost(id, currentUser)));
    }

    // ── Tracking ──────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/posts/{id}/view
     * Public endpoint to increment view count on the client side.
     */
    @PatchMapping("/{id}/view")
    public ResponseEntity<Void> incrementView(@PathVariable UUID id) {
        postService.incrementView(id);
        return ResponseEntity.noContent().build();
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        postService.deletePost(id, currentUser);
        return ResponseEntity.ok(ApiResponse.ok("Post deleted"));
    }

    // ── Discovery: Trending + Featured ───────────────────────────────────────

    /**
     * GET /api/v1/posts/trending
     *
     * <p>Public endpoint. Returns the top posts by view count published in the
     * last N days (configured via {@code app.cache.trending-window-days}).
     * Result is cached in Redis.
     */
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<PostListItemResponse>>> getTrending() {
        return ResponseEntity.ok(ApiResponse.ok(postService.getTrending()));
    }

    /**
     * GET /api/v1/posts/featured
     *
     * <p>Public endpoint. Returns the admin-curated featured post list.
     * Result is cached in Redis.
     */
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<PostListItemResponse>>> getFeatured() {
        return ResponseEntity.ok(ApiResponse.ok(postService.getFeatured()));
    }

    // ── Public list ───────────────────────────────────────────────────────────

    /**
     * Public endpoint — JWT is optional.
     * When authenticated, {@code likedByCurrentUser} is populated accurately.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<PostListItemResponse>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) UUID   authorId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser // null if unauthenticated
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        PaginatedResponse<PostListItemResponse> result = postService.getPublicList(
                category, tag, authorId, q, sort, pageable, currentUser);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Single post by slug ───────────────────────────────────────────────────

    /**
     * Public endpoint — JWT is optional.
     * Drafts are only visible to the author (returns 404 for everyone else).
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<PostResponse>> getBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(postService.getBySlug(slug, currentUser)));
    }

    // ── My posts ──────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostListItemResponse>>> myPosts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ResponseEntity.ok(ApiResponse.ok(postService.getMyPosts(currentUser, pageable)));
    }
}
