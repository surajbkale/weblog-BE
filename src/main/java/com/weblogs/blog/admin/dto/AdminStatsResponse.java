package com.weblogs.blog.admin.dto;

/**
 * Aggregated platform statistics for {@code GET /api/v1/admin/stats}.
 */
public record AdminStatsResponse(
        long totalUsers,
        long totalPosts,        // all statuses, not hard-deleted
        long totalPublished,
        long totalComments,     // non-soft-deleted
        long totalLikes
) {}
