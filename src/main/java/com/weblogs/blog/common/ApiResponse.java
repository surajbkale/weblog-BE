package com.weblogs.blog.common;

import java.util.List;

/**
 * Uniform JSON envelope for every API response.
 * <pre>
 * { "success": true,  "data": {...}, "message": "OK",    "errors": null }
 * { "success": false, "data": null,  "message": "...",   "errors": ["..."] }
 * </pre>
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        List<String> errors
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> validationError(List<String> errors) {
        return new ApiResponse<>(false, null, "Validation failed", errors);
    }
}
