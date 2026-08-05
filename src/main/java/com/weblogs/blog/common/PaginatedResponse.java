package com.weblogs.blog.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response envelope for all paginated list endpoints.
 *
 * <pre>
 * {
 *   "content": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 100,
 *   "totalPages": 5
 * }
 * </pre>
 */
public record PaginatedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /** Convenience factory from a Spring Data {@link Page}. */
    public static <T> PaginatedResponse<T> from(Page<T> page) {
        return new PaginatedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
