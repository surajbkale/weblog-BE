package com.weblogs.blog.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePostRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @NotBlank(message = "Content is required")
        String content,

        @Size(max = 1000, message = "Excerpt must not exceed 1000 characters")
        String excerpt,

        @Size(max = 500, message = "Cover image URL must not exceed 500 characters")
        String coverImageUrl,

        /** Category IDs to attach. Categories must already exist (admin-managed). */
        List<UUID> categoryIds,

        /** Tag names to attach. Unknown tags are created automatically. */
        List<String> tagNames
) {}
