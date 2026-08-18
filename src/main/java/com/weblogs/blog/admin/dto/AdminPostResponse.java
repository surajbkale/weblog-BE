package com.weblogs.blog.admin.dto;

import com.weblogs.blog.post.Post;

import java.time.Instant;
import java.util.UUID;

/**
 * Extended post summary for the admin post-list endpoint.
 * Includes {@code deleted} flag and author contact info for moderation purposes.
 */
public record AdminPostResponse(
        UUID    id,
        String  title,
        String  slug,
        String  status,
        boolean deleted,
        long    viewCount,
        long    likeCount,
        long    commentCount,
        String  authorEmail,
        String  authorDisplayName,
        Instant publishedAt,
        Instant createdAt
) {
    public static AdminPostResponse from(Post post, long likeCount, long commentCount) {
        return new AdminPostResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getStatus().name(),
                post.isDeleted(),
                post.getViewCount(),
                likeCount,
                commentCount,
                post.getAuthor().getEmail(),
                post.getAuthor().getDisplayName(),
                post.getPublishedAt(),
                post.getCreatedAt()
        );
    }
}
