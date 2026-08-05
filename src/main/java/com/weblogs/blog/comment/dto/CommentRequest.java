package com.weblogs.blog.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CommentRequest(

        @NotBlank(message = "Comment content is required")
        @Size(max = 5000, message = "Comment must not exceed 5000 characters")
        String content,

        /** Optional — set to the ID of the parent comment when creating a reply. */
        UUID parentId
) {}
