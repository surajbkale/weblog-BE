package com.weblogs.blog.auth.dto;

import com.weblogs.blog.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String token,
        @StrongPassword String newPassword
) {}
