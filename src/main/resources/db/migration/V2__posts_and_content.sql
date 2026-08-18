-- ─────────────────────────────────────────────────────────────────────────────
-- V2__posts_and_content.sql  ·  Blog content: categories, tags, posts,
--                                              comments, likes
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Categories ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

-- ── Tags ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS tags (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tags_name UNIQUE (name),
    CONSTRAINT uq_tags_slug UNIQUE (slug)
);

-- ── Posts ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(500)  NOT NULL,
    slug            VARCHAR(600)  NOT NULL,
    content         TEXT          NOT NULL,
    excerpt         VARCHAR(1000),
    cover_image_url VARCHAR(500),
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    author_id       UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    published_at    TIMESTAMPTZ,
    view_count      BIGINT        NOT NULL DEFAULT 0,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    search_vector   TSVECTOR,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_posts_slug UNIQUE (slug)
);

CREATE INDEX idx_posts_author_id      ON posts(author_id);
CREATE INDEX idx_posts_status         ON posts(status);
CREATE INDEX idx_posts_deleted        ON posts(deleted);
CREATE INDEX idx_posts_published_at   ON posts(published_at DESC);
CREATE INDEX idx_posts_search_vector  ON posts USING GIN (search_vector);

-- ── Full-text search trigger ──────────────────────────────────────────────────
-- Populates search_vector automatically on INSERT or UPDATE.
-- title gets weight 'A' (highest), content gets weight 'B'.

CREATE OR REPLACE FUNCTION posts_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title,   '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.content, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_posts_search_vector
    BEFORE INSERT OR UPDATE OF title, content ON posts
    FOR EACH ROW EXECUTE FUNCTION posts_search_vector_update();

-- ── Post ↔ Category join table ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS post_categories (
    post_id     UUID NOT NULL REFERENCES posts(id)      ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,

    PRIMARY KEY (post_id, category_id)
);

CREATE INDEX idx_post_categories_category_id ON post_categories(category_id);

-- ── Post ↔ Tag join table ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS post_tags (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag_id  UUID NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,

    PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX idx_post_tags_tag_id ON post_tags(tag_id);

-- ── Comments ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id    UUID    NOT NULL REFERENCES posts(id)  ON DELETE CASCADE,
    author_id  UUID    NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    parent_id  UUID             REFERENCES comments(id) ON DELETE SET NULL,
    content    TEXT    NOT NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_post_id   ON comments(post_id);
CREATE INDEX idx_comments_author_id ON comments(author_id);
CREATE INDEX idx_comments_parent_id ON comments(parent_id);

-- ── Likes ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS likes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id    UUID        NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_likes_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX idx_likes_post_id ON likes(post_id);
CREATE INDEX idx_likes_user_id ON likes(user_id);
