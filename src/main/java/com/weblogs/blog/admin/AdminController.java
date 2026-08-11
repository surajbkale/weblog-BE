package com.weblogs.blog.admin;

import com.weblogs.blog.admin.dto.*;
import com.weblogs.blog.common.ApiResponse;
import com.weblogs.blog.common.PaginatedResponse;
import com.weblogs.blog.post.PostStatus;
import com.weblogs.blog.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-panel REST controller — all routes require {@code ROLE_ADMIN}.
 *
 * <p>URL-level security is enforced in {@code SecurityConfig}
 * ({@code /api/v1/admin/**} → {@code hasRole('ADMIN')}).
 * Method-level {@code @PreAuthorize} inside {@link AdminService} acts as
 * defence-in-depth.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ── Users ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/users
     *
     * <p>Returns a paginated list of all users.
     * Optional {@code ?q=} parameter searches display name and email.
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(q, pageable)));
    }

    /**
     * PATCH /api/v1/admin/users/{id}/role
     *
     * <p>Promotes a user to ADMIN or demotes them back to USER.
     * Admins cannot demote themselves.
     */
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleChangeRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                adminService.changeRole(id, request, currentUser)));
    }

    /**
     * PATCH /api/v1/admin/users/{id}/status
     *
     * <p>Suspends ({@code active=false}) or reinstates ({@code active=true}) a user account.
     * Suspended users receive 401 on all subsequent authenticated requests.
     * Admins cannot suspend themselves.
     */
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> setUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                adminService.setUserActive(id, request, currentUser)));
    }

    // ── Posts ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/posts
     *
     * <p>Returns all posts across all users (all statuses, including soft-deleted).
     * Optional {@code ?status=PUBLISHED|DRAFT} filters by post status.
     */
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminPostResponse>>> listPosts(
            @RequestParam(required = false) PostStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.ok(adminService.listAllPosts(status, pageable)));
    }

    /**
     * DELETE /api/v1/admin/posts/{id}
     *
     * <p>Permanently removes a post and all associated likes/comments (via DB CASCADE).
     * Unlike the author soft-delete, this operation is irreversible.
     */
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<Void>> hardDeletePost(@PathVariable UUID id) {
        adminService.hardDeletePost(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * PATCH /api/v1/admin/posts/{id}/restore
     *
     * <p>Restores a soft-deleted post, returning it to DRAFT status.
     * The author must explicitly re-publish it to make it public.
     */
    @PatchMapping("/posts/{id}/restore")
    public ResponseEntity<ApiResponse<AdminPostResponse>> restorePost(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.restorePost(id)));
    }

    /**
     * PATCH /api/v1/admin/posts/{id}/featured?featured=true|false
     *
     * <p>Adds or removes a post from the admin-curated featured list.
     * Setting {@code featured=true} makes the post appear in
     * {@code GET /api/v1/posts/featured}. The featured cache is evicted immediately.
     */
    @PatchMapping("/posts/{id}/featured")
    public ResponseEntity<ApiResponse<AdminPostResponse>> setFeatured(
            @PathVariable UUID id,
            @RequestParam boolean featured) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.setFeatured(id, featured)));
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/stats
     *
     * <p>Returns aggregated platform statistics:
     * all-time totals (users, posts, published posts, comments, likes, views)
     * plus a 7-day rolling window for growth monitoring.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getStats()));
    }
}
