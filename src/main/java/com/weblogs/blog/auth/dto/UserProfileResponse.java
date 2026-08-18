package com.weblogs.blog.auth.dto;

import com.weblogs.blog.user.AuthProvider;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String bio,
        Role role,
        AuthProvider authProvider,
        boolean emailVerified,
        long publishedPostCount,
        Instant memberSince
) {
    /** Used by UserController — requires post count fetched separately. */
    public static UserProfileResponse from(User user, long publishedPostCount) {
        // Defensive fallback: if displayName was never set, derive it from the email prefix.
        String safeDisplayName = (user.getDisplayName() != null && !user.getDisplayName().isBlank())
                ? user.getDisplayName()
                : user.getEmail().split("@")[0];

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                safeDisplayName,
                user.getAvatarUrl(),
                user.getBio(),
                user.getRole(),
                user.getAuthProvider(),
                user.isEmailVerified(),
                publishedPostCount,
                user.getCreatedAt()
        );
    }

    /** Convenience overload for callers that don't need post count (e.g. auth responses). */
    public static UserProfileResponse from(User user) {
        return from(user, 0L);
    }
}
