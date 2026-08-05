package com.weblogs.blog.comment.dto;

import com.weblogs.blog.comment.Comment;
import com.weblogs.blog.post.dto.AuthorSummary;

import java.time.Instant;
import java.util.UUID;

/**
 * Flat comment response — includes {@code parentId} so the frontend can
 * reconstruct the thread tree. Soft-deleted comments have {@code deleted=true}
 * and {@code content=null}; the frontend renders them as "[deleted]".
 */
public record CommentResponse(
        UUID         id,
        UUID         postId,
        AuthorSummary author,
        UUID         parentId,
        String       content,    // null when deleted
        boolean      deleted,
        Instant      createdAt,
        Instant      updatedAt
) {
    public static CommentResponse from(Comment c) {
        return new CommentResponse(
                c.getId(),
                c.getPost().getId(),
                AuthorSummary.from(c.getAuthor()),
                c.getParentId(),
                c.isDeleted() ? null : c.getContent(),
                c.isDeleted(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
