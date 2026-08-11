-- ─────────────────────────────────────────────────────────────────────────────
-- V3__user_bio.sql  ·  Add bio column + backfill null auth_provider values
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(500);

-- Backfill any rows where auth_provider was not set (created before the column
-- was mandatory). Defaults to LOCAL since those accounts used email/password.
UPDATE users SET auth_provider = 'LOCAL' WHERE auth_provider IS NULL;
