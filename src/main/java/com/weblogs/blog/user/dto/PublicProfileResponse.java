package com.weblogs.blog.user.dto;

import com.weblogs.blog.user.User;

import java.time.Instant;
import java.util.UUID;

public record PublicProfileResponse(
        UUID id,
        String displayName,
        String avatarUrl,
        String bio,
        long publishedPostCount,
        Instant memberSince
) {
    public static PublicProfileResponse from(User user, long publishedPostCount) {
        return new PublicProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getBio(),
                publishedPostCount,
                user.getCreatedAt()
        );
    }
}
