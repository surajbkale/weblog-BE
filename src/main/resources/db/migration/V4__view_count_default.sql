-- ─────────────────────────────────────────────────────────────────────────────
-- V4__view_count_default.sql · Harden view_count column default
-- ─────────────────────────────────────────────────────────────────────────────

-- Ensure the column has an explicit DB-level default of 0.
-- This guards fresh installs where the column existed without a server-side default.
ALTER TABLE posts ALTER COLUMN view_count SET DEFAULT 0;

-- Backfill any rows that somehow ended up NULL (defensive).
UPDATE posts SET view_count = 0 WHERE view_count IS NULL;
