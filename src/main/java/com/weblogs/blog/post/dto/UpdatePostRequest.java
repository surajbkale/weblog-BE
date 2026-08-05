package com.weblogs.blog.post.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** All fields are optional — only non-null fields are applied on update. */
public record UpdatePostRequest(

        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        String content,

        @Size(max = 1000, message = "Excerpt must not exceed 1000 characters")
        String excerpt,

        @Size(max = 500, message = "Cover image URL must not exceed 500 characters")
        String coverImageUrl,

        List<UUID> categoryIds,

        List<String> tagNames
) {}
