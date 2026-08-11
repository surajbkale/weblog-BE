package com.weblogs.blog.admin;

import com.weblogs.blog.admin.dto.AdminPostResponse;
import com.weblogs.blog.admin.dto.AdminStatsResponse;
import com.weblogs.blog.admin.dto.AdminUserResponse;
import com.weblogs.blog.admin.dto.RoleChangeRequest;
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
     * Each count is a simple indexed {@code COUNT(*)} — no caching needed.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalUsers     = userRepository.count();
        long totalPosts     = postRepository.count();      // all rows, including soft-deleted
        long totalPublished = postRepository.countByStatusAndDeletedFalse(PostStatus.PUBLISHED);
        long totalComments  = commentRepository.countByDeletedFalse();
        long totalLikes     = likeRepository.count();

        return new AdminStatsResponse(
                totalUsers,
                totalPosts,
                totalPublished,
                totalComments,
                totalLikes
        );
    }
}
