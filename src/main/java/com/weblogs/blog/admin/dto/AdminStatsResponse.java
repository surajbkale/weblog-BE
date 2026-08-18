package com.weblogs.blog.admin.dto;

import java.time.Instant;

/**
 * Aggregated platform statistics for {@code GET /api/v1/admin/stats}.
 * Includes both all-time totals and a 7-day rolling window for growth monitoring.
 */
public record AdminStatsResponse(
        // ── All-time totals ──────────────────────────────────────────────────
        long totalUsers,
        long totalPosts,        // all statuses, not hard-deleted
        long totalPublished,
        long totalComments,     // non-soft-deleted
        long totalLikes,
        long totalViews,

        // ── Last-7-days window ───────────────────────────────────────────────
        long newUsersLast7Days,
        long newPostsLast7Days,
        long newCommentsLast7Days,

        // ── Computed at ──────────────────────────────────────────────────────
        Instant computedAt
) {}
