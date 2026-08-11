package com.weblogs.blog.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Returns a flat paginated list of all comments for a post (including soft-deleted ones
     * so that reply threading doesn't break). The frontend filters/renders deleted comments
     * as "[deleted]".
     */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.post.id = :postId
            ORDER BY c.createdAt ASC
            """)
    Page<Comment> findByPostId(@Param("postId") UUID postId, Pageable pageable);

    /** Total non-deleted comment count — used by the admin stats endpoint. */
    long countByDeletedFalse();
}
