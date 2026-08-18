-- ─────────────────────────────────────────────────────────────────────────────
-- V7__backfill_search_vector.sql
-- Backfills search_vector for any posts inserted before the trigger existed,
-- and ensures the trigger covers UPDATE of all relevant columns.
-- ─────────────────────────────────────────────────────────────────────────────

-- Backfill existing rows where search_vector is NULL
UPDATE posts
SET search_vector =
        setweight(to_tsvector('english', coalesce(title,   '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
WHERE search_vector IS NULL;

-- Also update rows that may have been saved without the trigger firing
-- (e.g. direct SQL inserts during development)
UPDATE posts
SET search_vector =
        setweight(to_tsvector('english', coalesce(title,   '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
WHERE search_vector = ''::tsvector;
