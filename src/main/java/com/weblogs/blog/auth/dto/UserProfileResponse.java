package com.weblogs.blog.auth.dto;

import com.weblogs.blog.user.AuthProvider;
import com.weblogs.blog.user.Role;
import com.weblogs.blog.user.User;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        Role role,
        AuthProvider authProvider,
        boolean emailVerified
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getAuthProvider(),
                user.isEmailVerified()
        );
    }
}
