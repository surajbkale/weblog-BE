-- ─────────────────────────────────────────────────────────────────────────────
-- V5__post_featured.sql  ·  Add admin-curated featured flag to posts
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index: only indexes the small subset of featured rows.
-- Makes GET /api/v1/posts/featured extremely fast even with millions of posts.
CREATE INDEX IF NOT EXISTS idx_posts_featured
    ON posts(featured)
    WHERE featured = TRUE;
