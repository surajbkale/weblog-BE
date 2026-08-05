package com.weblogs.blog.post.dto;

import com.weblogs.blog.user.User;

import java.util.UUID;

public record AuthorSummary(
        UUID   id,
        String displayName,
        String avatarUrl
) {
    public static AuthorSummary from(User user) {
        return new AuthorSummary(user.getId(), user.getDisplayName(), user.getAvatarUrl());
    }
}
