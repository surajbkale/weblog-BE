package com.weblogs.blog.user;

import com.weblogs.blog.auth.dto.UserProfileResponse;
import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.common.PaginatedResponse;
import com.weblogs.blog.post.PostService;
import com.weblogs.blog.post.dto.PostListItemResponse;
import com.weblogs.blog.user.dto.ChangePasswordRequest;
import com.weblogs.blog.user.dto.PublicProfileResponse;
import com.weblogs.blog.user.dto.UpdateProfileRequest;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PostService postService;

    /**
     * GET /api/v1/users/me — authenticated user's full profile.
     *
     * <p>The JwtAuthFilter silently skips unauthenticated requests, causing
     * Spring's AnonymousAuthenticationFilter to inject an anonymous principal.
     * {@code @AuthenticationPrincipal User user} resolves to {@code null} for
     * anonymous requests (type mismatch). We guard here explicitly instead of
     * letting a NullPointerException propagate to UserService.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(user)));
    }

    /** PUT /api/v1/users/me — update display name, bio, avatar URL */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest req) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(user, req)));
    }

    /** PUT /api/v1/users/me/password — change password (LOCAL accounts only) */
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest req) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        userService.changePassword(user, req);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** GET /api/v1/users/{id} — public profile (unauthenticated access allowed) */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicProfileResponse>> getPublicProfile(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getPublicProfile(id)));
    }

    /**
     * GET /api/v1/users/{id}/posts — paginated list of published posts by this author.
     *
     * <p>This endpoint is dedicated to author profile pages and is <b>always cached</b>
     * regardless of authentication state (unlike {@code GET /api/v1/posts?authorId=...}
     * which skips the cache for authenticated users to keep {@code likedByCurrentUser}
     * accurate). Author profile pages don't show per-user liked state, so the cached
     * response is safe for all callers.
     *
     * <p>Cache TTL matches the global post-list TTL (default 5 min). The cache is evicted
     * automatically when the author publishes, updates, or deletes a post.
     *
     * @param id   the author's UUID
     * @param sort sort order: "newest" (default), "oldest", "popular"
     * @param page zero-based page index
     * @param size page size (capped at 50)
     */
    @GetMapping("/{id}/posts")
    public ResponseEntity<ApiResponse<PaginatedResponse<PostListItemResponse>>> getAuthorPosts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        PaginatedResponse<PostListItemResponse> result =
                postService.getAuthorPosts(id, sort, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
