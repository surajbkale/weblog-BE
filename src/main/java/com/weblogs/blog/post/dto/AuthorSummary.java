package com.weblogs.blog.post.dto;

import com.weblogs.blog.user.User;

import java.util.UUID;

/**
 * Compact author projection embedded in {@link PostResponse} and {@link PostListItemResponse}.
 * Carries enough information for author cards and hover-cards on the frontend.
 * Intentionally omits sensitive fields (email, passwordHash, role, etc.).
 */
public record AuthorSummary(
        UUID   id,
        String displayName,
        String avatarUrl,
        String bio              // may be null if the author hasn't set one
) {
    public static AuthorSummary from(User user) {
        return new AuthorSummary(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getBio()
        );
    }
}
