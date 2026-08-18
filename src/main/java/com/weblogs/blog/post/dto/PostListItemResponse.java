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
 * Reading time is estimated from the stored {@code content} so frontends
 * can display it in list cards without fetching the full post.
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
        long    likeCount,
        long    commentCount,
        long    viewCount,
        boolean likedByCurrentUser,
        int     readingTimeMinutes,     // ceil(word_count / 200)
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
                computeReadingTime(post.getContent()),
                post.getPublishedAt(),
                post.getCreatedAt()
        );
    }

    /** Estimates reading time at 200 words per minute (average adult reading speed). */
    private static int computeReadingTime(String content) {
        if (content == null || content.isBlank()) return 0;
        int wordCount = content.trim().split("\\s+").length;
        return (int) Math.max(1, Math.ceil(wordCount / 200.0));
    }
}
