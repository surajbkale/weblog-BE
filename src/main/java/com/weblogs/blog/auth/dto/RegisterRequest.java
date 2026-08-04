package com.weblogs.blog.auth.dto;

import com.weblogs.blog.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email
        String email,

        @StrongPassword
        String password,

        @NotBlank @Size(min = 2, max = 100)
        String displayName
) {}
