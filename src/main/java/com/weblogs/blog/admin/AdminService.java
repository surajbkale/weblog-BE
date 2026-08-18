package com.weblogs.blog.admin;

import com.weblogs.blog.admin.dto.*;
import com.weblogs.blog.cache.CacheService;
import com.weblogs.blog.comment.CommentRepository;
import com.weblogs.blog.common.PaginatedResponse;
import com.weblogs.blog.exception.ForbiddenException;
import com.weblogs.blog.exception.NotFoundException;
import com.weblogs.blog.like.LikeRepository;
import com.weblogs.blog.post.Post;
import com.weblogs.blog.post.PostRepository;
import com.weblogs.blog.post.PostStatus;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;
import com.weblogs.blog.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Business logic for all admin-panel operations.
 *
 * <p>Every public method carries {@code @PreAuthorize("hasRole('ADMIN')")} as
 * defence-in-depth — the URL-level rule in {@code SecurityConfig} is the primary
 * gate, this annotation ensures the check survives any future refactoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Duration STATS_WINDOW = Duration.ofDays(7);

    private final UserRepository    userRepository;
    private final PostRepository    postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository    likeRepository;
    private final CacheService      cacheService;

    // ── Users ─────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated, optionally filtered list of all users.
     *
     * @param q        optional search term matched against display name and email
     * @param pageable pagination and sort parameters
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PaginatedResponse<AdminUserResponse> listUsers(String q, Pageable pageable) {
        // Pass null to return all users; non-blank q triggers LIKE search
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Page<User> page = userRepository.searchUsers(query, pageable);
        return PaginatedResponse.from(page.map(AdminUserResponse::from));
    }

    /**
     * Promotes or demotes a user's role.
     *
     * <p>Guards against self-demotion so an admin cannot accidentally lock
     * themselves out of the admin panel.
     *
     * @param targetUserId  ID of the user to modify
     * @param request       the desired new role
     * @param currentUser   the authenticated admin performing the action
     * @return the updated user projection
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminUserResponse changeRole(UUID targetUserId,
                                        RoleChangeRequest request,
                                        User currentUser) {
        // Self-demotion guard
        if (currentUser.getId().equals(targetUserId) && request.role() != Role.ADMIN) {
            throw new ForbiddenException("Admins cannot remove their own ADMIN role");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        target.setRole(request.role());
        User saved = userRepository.save(target);
        log.info("Admin {} changed role of user {} to {}",
                currentUser.getId(), targetUserId, request.role());
        return AdminUserResponse.from(saved);
    }

    /**
     * Suspends or reinstates a user account.
     *
     * <p>Setting {@code active = false} marks the account as suspended.
     * The user's existing JWTs will be rejected by {@code JwtAuthFilter}
     * on the next request (the {@code active} claim is baked into the JWT).
     * Self-suspension is prevented so an admin cannot lock themselves out.
     *
     * @param targetUserId ID of the user to modify
     * @param request      contains the desired {@code active} flag
     * @param currentUser  the authenticated admin performing the action
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminUserResponse setUserActive(UUID targetUserId,
                                           UserStatusRequest request,
                                           User currentUser) {
        if (currentUser.getId().equals(targetUserId) && !request.active()) {
            throw new ForbiddenException("Admins cannot suspend their own account");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        target.setActive(request.active());
        User saved = userRepository.save(target);
        log.info("Admin {} set active={} for user {}", currentUser.getId(), request.active(), targetUserId);
        return AdminUserResponse.from(saved);
    }

    // ── Posts ─────────────────────────────────────────────────────────────────

    /**
     * Returns all posts across all users for the admin panel.
     *
     * @param status   optional status filter; {@code null} returns all statuses
     * @param pageable pagination parameters
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PaginatedResponse<AdminPostResponse> listAllPosts(PostStatus status, Pageable pageable) {
        Page<Post> page = postRepository.findAllForAdmin(status, pageable);
        return PaginatedResponse.from(page.map(post -> {
            long likeCount    = postRepository.countLikesByPostId(post.getId());
            long commentCount = postRepository.countCommentsByPostId(post.getId());
            return AdminPostResponse.from(post, likeCount, commentCount);
        }));
    }

    /**
     * Hard-deletes a post permanently.
     * Associated likes and comments are removed by DB CASCADE.
     * Redis caches for the post and all list pages are evicted.
     *
     * @param postId the post to delete
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void hardDeletePost(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));

        String slug = post.getSlug();
        postRepository.hardDeleteById(postId);

        // Evict all Redis caches so stale data is not served after deletion
        cacheService.evict(CacheService.POST_SLUG_PREFIX + slug);
        cacheService.evictAllPostListCaches();
        cacheService.evict(CacheService.TRENDING_POSTS);
        cacheService.evict(CacheService.FEATURED_POSTS);

        log.info("Admin hard-deleted post id={} slug={}", postId, slug);
    }

    /**
     * Restores a soft-deleted post (sets {@code deleted = false}).
     * The post is returned to DRAFT status and becomes visible again to its author.
     * To make it public, it must be explicitly re-published.
     *
     * @param postId the soft-deleted post to restore
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminPostResponse restorePost(UUID postId) {
        // Use findById (not the public helper) so we can find deleted posts too
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));

        if (!post.isDeleted()) {
            throw new ForbiddenException("Post is not deleted and does not need to be restored");
        }

        post.setDeleted(false);
        post.setStatus(PostStatus.DRAFT);   // put back as draft, not directly published
        postRepository.save(post);

        cacheService.evictAllPostListCaches();

        log.info("Admin restored soft-deleted post id={}", postId);
        long likeCount    = postRepository.countLikesByPostId(postId);
        long commentCount = postRepository.countCommentsByPostId(postId);
        return AdminPostResponse.from(post, likeCount, commentCount);
    }

    /**
     * Toggles the {@code featured} flag on a post.
     *
     * <p>Setting {@code featured = true} makes the post appear in
     * {@code GET /api/v1/posts/featured}. The Redis featured-list cache is
     * evicted immediately so the next request reflects the change.
     *
     * @param postId   the post to feature or unfeature
     * @param featured {@code true} to feature, {@code false} to unfeature
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminPostResponse setFeatured(UUID postId, boolean featured) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));

        post.setFeatured(featured);
        postRepository.save(post);
        cacheService.evict(CacheService.FEATURED_POSTS);

        long likeCount    = postRepository.countLikesByPostId(postId);
        long commentCount = postRepository.countCommentsByPostId(postId);

        log.info("Admin set featured={} for post id={}", featured, postId);
        return AdminPostResponse.from(post, likeCount, commentCount);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Aggregates platform-wide counts for the admin dashboard.
     * Includes all-time totals and a rolling 7-day window for growth monitoring.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        Instant since7d = Instant.now().minus(STATS_WINDOW);

        long totalUsers     = userRepository.count();
        long totalPosts     = postRepository.count();
        long totalPublished = postRepository.countByStatusAndDeletedFalse(PostStatus.PUBLISHED);
        long totalComments  = commentRepository.countByDeletedFalse();
        long totalLikes     = likeRepository.count();
        long totalViews     = postRepository.sumAllViewCounts();

        long newUsers7d    = userRepository.countByCreatedAtAfter(since7d);
        long newPosts7d    = postRepository.countByCreatedAtAfterAndDeletedFalse(since7d);
        long newComments7d = commentRepository.countByCreatedAtAfterAndDeletedFalse(since7d);

        return new AdminStatsResponse(
                totalUsers,
                totalPosts,
                totalPublished,
                totalComments,
                totalLikes,
                totalViews,
                newUsers7d,
                newPosts7d,
                newComments7d,
                Instant.now()
        );
    }
}
