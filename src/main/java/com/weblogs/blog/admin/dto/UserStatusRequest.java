package com.weblogs.blog.admin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/admin/users/{id}/status}.
 * Setting {@code active = false} suspends the account; {@code true} restores it.
 */
public record UserStatusRequest(
        @NotNull(message = "active must be true or false")
        Boolean active
) {}
