package com.weblogs.blog.tag.dto;

import com.weblogs.blog.tag.Tag;

import java.util.UUID;

public record TagResponse(
        UUID   id,
        String name,
        String slug
) {
    public static TagResponse from(Tag t) {
        return new TagResponse(t.getId(), t.getName(), t.getSlug());
    }
}
