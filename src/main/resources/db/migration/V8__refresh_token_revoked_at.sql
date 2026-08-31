-- V8: Add revoked_at timestamp to refresh_tokens
--
-- Purpose: Track WHEN a refresh token was revoked so AuthService can
-- distinguish a benign race condition (React Strict Mode double-invoke,
-- fast page reload) from a real token reuse attack.
--
-- Logic in AuthService.refresh():
--   - revoked within  ≤ 10 s  → likely a race → silent 401, no session wipe
--   - revoked more than 10 s ago → real attack → revoke all sessions
--
-- The column is nullable: NULL means the token was never revoked (active).

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;
