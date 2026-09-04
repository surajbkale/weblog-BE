-- ─────────────────────────────────────────────────────────────────────────────
-- V10__posts_like_count.sql
--
-- Materializes like counts into a posts.like_count column so that
-- ORDER BY like_count DESC uses an index scan instead of a correlated
-- subquery that re-counts the likes table for every row on every page load.
--
-- Before this migration the findPublished ORDER BY clause contained:
--   CASE WHEN :sort = 'mostLiked' THEN
--       (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id)
--   END DESC NULLS LAST
--
-- For a page of 20 posts this fired 20 extra COUNT(*) subqueries on every
-- "most liked" sort request that was a cache miss.  With a materialized
-- like_count column and an index on it, the sort is a single index scan.
--
-- Maintenance strategy: a BEFORE/AFTER trigger on the likes table increments /
-- decrements like_count atomically.  No application-level maintenance is
-- required — any path that inserts or deletes a like row (JPA, raw SQL,
-- future batch jobs) automatically keeps the column consistent.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Add the column (idempotent)
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS like_count BIGINT NOT NULL DEFAULT 0;

-- 2. Backfill existing rows from the likes table
UPDATE posts p
SET    like_count = (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id);

-- 3. Trigger function — increments on INSERT, decrements (floor 0) on DELETE
CREATE OR REPLACE FUNCTION trg_update_post_like_count()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE posts SET like_count = like_count + 1       WHERE id = NEW.post_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE posts SET like_count = GREATEST(like_count - 1, 0) WHERE id = OLD.post_id;
    END IF;
    RETURN NULL; -- AFTER trigger, return value is ignored for row-level
END;
$$;

-- 4. Attach trigger to the likes table
--    DROP + CREATE is idempotent and safer than IF NOT EXISTS (Postgres < 14)
DROP TRIGGER IF EXISTS trg_likes_update_like_count ON likes;
CREATE TRIGGER trg_likes_update_like_count
    AFTER INSERT OR DELETE ON likes
    FOR EACH ROW EXECUTE FUNCTION trg_update_post_like_count();

-- 5. Index for ORDER BY like_count DESC  (used by the "mostLiked" sort)
CREATE INDEX IF NOT EXISTS idx_posts_like_count ON posts (like_count DESC);
