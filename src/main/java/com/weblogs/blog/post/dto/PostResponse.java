package com.weblogs.blog.post.dto;

import com.weblogs.blog.category.dto.CategoryResponse;
import com.weblogs.blog.post.Post;
import com.weblogs.blog.tag.dto.TagResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full post response — includes {@code content}.
 * Used for single-post retrieval ({@code GET /api/v1/posts/{slug}}).
 */
public record PostResponse(
        UUID   id,
        String title,
        String slug,
        String content,
        String excerpt,
        String coverImageUrl,
        String status,
        AuthorSummary author,
        List<CategoryResponse> categories,
        List<TagResponse>      tags,
        long   likeCount,
        long   commentCount,
        boolean likedByCurrentUser,
        Instant publishedAt,
        Instant createdAt
) {
    public static PostResponse from(Post post,
                                    long likeCount,
                                    long commentCount,
                                    boolean likedByCurrentUser) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getContent(),
                post.getExcerpt(),
                post.getCoverImageUrl(),
                post.getStatus().name(),
                AuthorSummary.from(post.getAuthor()),
                post.getCategories().stream().map(CategoryResponse::from).toList(),
                post.getTags().stream().map(TagResponse::from).toList(),
                likeCount,
                commentCount,
                likedByCurrentUser,
                post.getPublishedAt(),
                post.getCreatedAt()
        );
    }
}
