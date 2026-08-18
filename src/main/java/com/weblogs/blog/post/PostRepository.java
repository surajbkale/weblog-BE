package com.weblogs.blog.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findBySlugAndDeletedFalse(String slug);

    boolean existsBySlug(String slug);

    // ── Current user's own posts (all statuses) ───────────────────────────────

    @Query("""
            SELECT p FROM Post p
            WHERE p.author.id = :authorId
              AND p.deleted   = false
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findByAuthorId(@Param("authorId") UUID authorId, Pageable pageable);

    // ── Public list: published + not deleted ──────────────────────────────────
    // Supports optional category slug, tag slug, authorId, and full-text search.
    // When q is provided we rank by ts_rank; otherwise order by publishedAt DESC.

    @Query(value = """
            SELECT p.*
            FROM   posts p
            JOIN   users u ON u.id = p.author_id
            WHERE  p.deleted  = false
              AND  p.status   = 'PUBLISHED'
              AND  (CAST(:categorySlug AS TEXT) IS NULL OR EXISTS (
                       SELECT 1 FROM post_categories pc
                       JOIN   categories c ON c.id = pc.category_id
                       WHERE  pc.post_id = p.id AND c.slug = CAST(:categorySlug AS TEXT)))
              AND  (CAST(:tagSlug AS TEXT) IS NULL OR EXISTS (
                       SELECT 1 FROM post_tags pt
                       JOIN   tags t ON t.id = pt.tag_id
                       WHERE  pt.post_id = p.id AND t.slug = CAST(:tagSlug AS TEXT)))
              AND  (CAST(:authorId AS TEXT) IS NULL OR p.author_id = CAST(:authorId AS uuid))
              AND  (CAST(:q AS TEXT) IS NULL
                       OR p.search_vector @@ plainto_tsquery('english', CAST(:q AS TEXT)))
            ORDER BY
                CASE WHEN :sort = 'mostLiked' THEN
                    (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id)
                END DESC NULLS LAST,
                CASE WHEN :sort = 'relevance' AND CAST(:q AS TEXT) IS NOT NULL THEN
                    ts_rank(p.search_vector, plainto_tsquery('english', CAST(:q AS TEXT)))
                END DESC NULLS LAST,
                CASE WHEN :sort NOT IN ('mostLiked', 'relevance', 'oldest') THEN
                    p.published_at
                END DESC NULLS LAST,
                CASE WHEN :sort = 'oldest' THEN
                    p.published_at
                END ASC NULLS LAST
            """,
            countQuery = """
            SELECT COUNT(p.id)
            FROM   posts p
            WHERE  p.deleted  = false
              AND  p.status   = 'PUBLISHED'
              AND  (CAST(:categorySlug AS TEXT) IS NULL OR EXISTS (
                       SELECT 1 FROM post_categories pc
                       JOIN   categories c ON c.id = pc.category_id
                       WHERE  pc.post_id = p.id AND c.slug = CAST(:categorySlug AS TEXT)))
              AND  (CAST(:tagSlug AS TEXT) IS NULL OR EXISTS (
                       SELECT 1 FROM post_tags pt
                       JOIN   tags t ON t.id = pt.tag_id
                       WHERE  pt.post_id = p.id AND t.slug = CAST(:tagSlug AS TEXT)))
              AND  (CAST(:authorId AS TEXT) IS NULL OR p.author_id = CAST(:authorId AS uuid))
              AND  (CAST(:q AS TEXT) IS NULL
                       OR p.search_vector @@ plainto_tsquery('english', CAST(:q AS TEXT)))
            """,
            nativeQuery = true)
    Page<Post> findPublished(
            @Param("categorySlug") String categorySlug,
            @Param("tagSlug")      String tagSlug,
            @Param("authorId")     String authorId,
            @Param("q")            String q,
            @Param("sort")         String sort,
            Pageable pageable);

    // ── Counts (used to populate likeCount / commentCount on responses) ────────

    @Query("SELECT COUNT(l) FROM Like l WHERE l.post.id = :postId")
    long countLikesByPostId(@Param("postId") UUID postId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId AND c.deleted = false")
    long countCommentsByPostId(@Param("postId") UUID postId);

    // ── Batch count queries (N+1 fix for list views) ──────────────────────────

    /**
     * Returns [postId, likeCount] pairs for all supplied post IDs in a single query.
     * Used by PostService to batch-load like counts for a page of posts.
     */
    @Query("SELECT l.post.id, COUNT(l) FROM Like l WHERE l.post.id IN :postIds GROUP BY l.post.id")
    List<Object[]> findLikeCountsByPostIds(@Param("postIds") Collection<UUID> postIds);

    /**
     * Returns [postId, commentCount] pairs for all supplied post IDs in a single query.
     * Used by PostService to batch-load comment counts for a page of posts.
     */
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds AND c.deleted = false GROUP BY c.post.id")
    List<Object[]> findCommentCountsByPostIds(@Param("postIds") Collection<UUID> postIds);

    /**
     * Returns the subset of the given post IDs that the specified user has liked.
     * Used by PostService to batch-load the likedByCurrentUser flag in a single query.
     */
    @Query("SELECT l.post.id FROM Like l WHERE l.post.id IN :postIds AND l.user.id = :userId")
    List<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);

    /** Counts posts matching the given status that are not soft-deleted. Used by admin stats. */
    long countByStatusAndDeletedFalse(PostStatus status);

    // ── View count flush (called by ViewCountService scheduler) ──────────────

    /**
     * Additively increments {@code view_count} for a single post.
     * Using {@code view_count + :delta} (not SET to an absolute value) means
     * concurrent flushes never overwrite each other.
     */
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + :delta WHERE p.id = :id")
    void incrementViewCount(@Param("id") UUID id, @Param("delta") long delta);

    // ── Admin queries ─────────────────────────────────────────────────────────

    /**
     * Returns all posts for the admin panel — all statuses, including soft-deleted.
     * Optionally filtered by {@code status} (pass {@code null} to return everything).
     */
    @Query("""
            SELECT p FROM Post p
            WHERE (:status IS NULL OR p.status = :status)
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findAllForAdmin(@Param("status") PostStatus status, Pageable pageable);

    /**
     * Permanently removes a post row.
     * Associated likes and comments are cleaned up via ON DELETE CASCADE.
     */
    @Modifying
    @Query("DELETE FROM Post p WHERE p.id = :id")
    void hardDeleteById(@Param("id") UUID id);

    // ── SEO / Discovery queries ───────────────────────────────────────────────

    /**
     * Trending: published posts within the lookback window, ordered by view count.
     * The {@code publishedAt >= since} filter limits to recently published content;
     * {@code viewCount DESC} ranking favours currently hot posts.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.status     = com.weblogs.blog.post.PostStatus.PUBLISHED
              AND p.deleted    = false
              AND p.publishedAt >= :since
            ORDER BY p.viewCount DESC
            """)
    List<Post> findTrending(@Param("since") Instant since, Pageable pageable);

    /**
     * Featured: admin-curated published posts, newest first.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.status   = com.weblogs.blog.post.PostStatus.PUBLISHED
              AND p.deleted  = false
              AND p.featured = true
            ORDER BY p.publishedAt DESC
            """)
    List<Post> findFeatured(Pageable pageable);

    /**
     * Latest published posts — used by the RSS feed and sitemap.
     * Returns all published posts (no filter beyond status) ordered by publication date.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.status  = com.weblogs.blog.post.PostStatus.PUBLISHED
              AND p.deleted = false
            ORDER BY p.publishedAt DESC
            """)
    List<Post> findLatestPublished(Pageable pageable);

    /** Count non-deleted posts created after the given instant — used for time-series stats. */
    long countByCreatedAtAfterAndDeletedFalse(Instant since);

    /** Sum of all view_count values across all published non-deleted posts. */
    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Post p WHERE p.deleted = false")
    long sumAllViewCounts();
}
