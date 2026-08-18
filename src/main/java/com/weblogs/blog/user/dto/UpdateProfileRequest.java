package com.weblogs.blog.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
        String displayName,

        @Size(max = 500, message = "Bio must not exceed 500 characters")
        String bio,

        // URL from a prior POST /api/v1/media/upload — just stored as-is
        String avatarUrl
) {}
