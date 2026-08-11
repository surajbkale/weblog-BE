package com.weblogs.blog.post.dto;

import com.weblogs.blog.category.dto.CategoryResponse;
import com.weblogs.blog.post.Post;
import com.weblogs.blog.tag.dto.TagResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight post response for paginated list views — omits {@code content}
 * to avoid shipping full post bodies across the wire.
 * Used for {@code GET /api/v1/posts} and {@code GET /api/v1/posts/me}.
 */
public record PostListItemResponse(
        UUID   id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        String status,
        AuthorSummary author,
        List<CategoryResponse> categories,
        List<TagResponse>      tags,
        long   likeCount,
        long   commentCount,
        long   viewCount,
        boolean likedByCurrentUser,
        Instant publishedAt,
        Instant createdAt
) {
    public static PostListItemResponse from(Post post,
                                            long likeCount,
                                            long commentCount,
                                            boolean likedByCurrentUser) {
        return new PostListItemResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getExcerpt(),
                post.getCoverImageUrl(),
                post.getStatus().name(),
                AuthorSummary.from(post.getAuthor()),
                post.getCategories().stream().map(CategoryResponse::from).toList(),
                post.getTags().stream().map(TagResponse::from).toList(),
                likeCount,
                commentCount,
                post.getViewCount(),
                likedByCurrentUser,
                post.getPublishedAt(),
                post.getCreatedAt()
        );
    }
}
