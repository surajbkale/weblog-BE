package com.weblogs.blog.admin.dto;

import com.weblogs.blog.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of a {@link User} for the admin user-list endpoint.
 * Intentionally omits {@code passwordHash}, refresh tokens, and provider IDs.
 */
public record AdminUserResponse(
        UUID    id,
        String  email,
        String  displayName,
        String  avatarUrl,
        String  role,
        String  authProvider,
        boolean emailVerified,
        boolean active,
        Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getAuthProvider().name(),
                user.isEmailVerified(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
