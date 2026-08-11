package com.weblogs.blog.admin.dto;

import com.weblogs.blog.user.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/v1/admin/users/{id}/role}.
 */
public record RoleChangeRequest(
        @NotNull(message = "role must not be null")
        Role role
) {}
