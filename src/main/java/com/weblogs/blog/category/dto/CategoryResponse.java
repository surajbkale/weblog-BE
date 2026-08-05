package com.weblogs.blog.category.dto;

import com.weblogs.blog.category.Category;

import java.util.UUID;

public record CategoryResponse(
        UUID   id,
        String name,
        String slug
) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug());
    }
}
