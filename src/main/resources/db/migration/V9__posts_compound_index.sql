-- ─────────────────────────────────────────────────────────────────────────────
-- V9__posts_compound_index.sql
--
-- Adds a compound index on (status, deleted) to speed up the dominant query
-- pattern across the application:
--
--   WHERE p.status = 'PUBLISHED' AND p.deleted = FALSE
--
-- Individual indexes on status and deleted already exist (V2), but PostgreSQL
-- can only use one of them per query via bitmap index scan + merge, which is
-- less efficient than a single compound scan on (status, deleted).
--
-- This index covers:
--   - GET /api/v1/posts        (findPublished — main list)
--   - GET /api/v1/posts/{slug} (findBySlugAndDeletedFalse)
--   - GET /api/v1/posts/trending / /featured
--   - Admin list queries that filter on status
--
-- The column order (status first, deleted second) is intentional:
--   - status has low cardinality (DRAFT/PUBLISHED) but is the primary filter
--   - deleted=false is almost always true, so it's a narrow selector
--   Together they eliminate the most rows in the least I/O.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_posts_status_deleted
    ON posts (status, deleted);
