-- V6: Add `active` flag to users for account suspension
-- Default true so all existing accounts remain active after migration.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Index lets JwtAuthFilter efficiently check active status without full table scan
-- (used in admin queries filtering by active status)
CREATE INDEX IF NOT EXISTS idx_users_active ON users (active) WHERE active = FALSE;
