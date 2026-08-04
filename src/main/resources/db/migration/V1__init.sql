-- ─────────────────────────────────────────────────────────────────────────────
-- V1__init.sql  ·  Initial schema — users, refresh_tokens, auth_tokens
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    display_name  VARCHAR(100) NOT NULL,
    avatar_url    VARCHAR(500),
    auth_provider VARCHAR(20)  NOT NULL,      -- LOCAL | GOOGLE | GITHUB
    provider_id   VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,         -- SHA-256 hex of the raw token
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

CREATE TABLE IF NOT EXISTS auth_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,         -- SHA-256 hex of the raw token
    type        VARCHAR(30) NOT NULL,         -- EMAIL_VERIFICATION | PASSWORD_RESET
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_auth_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_tokens_token_hash ON auth_tokens(token_hash);
CREATE INDEX idx_auth_tokens_user_id    ON auth_tokens(user_id);
