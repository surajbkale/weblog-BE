package com.weblogs.blog.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn    // seconds
) {
    public static LoginResponse of(String accessToken, long expiresIn) {
        return new LoginResponse(accessToken, "Bearer", expiresIn);
    }
}
