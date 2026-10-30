CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    selected_collections TEXT[] NOT NULL,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notification_time TEXT NOT NULL DEFAULT '20:00',
    notification_time_zone TEXT NOT NULL DEFAULT 'Europe/Moscow'
);

CREATE TABLE IF NOT EXISTS artworks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist_id TEXT NOT NULL,
    artist_name TEXT NOT NULL,
    country TEXT NOT NULL,
    collection TEXT NOT NULL,
    year_value INTEGER NOT NULL,
    year_label TEXT NOT NULL,
    image_path TEXT NOT NULL,
    description TEXT NOT NULL,
    facts TEXT[] NOT NULL DEFAULT '{}',
    published_on DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS artwork_tags (
    artwork_id TEXT NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    tag_id TEXT NOT NULL,
    tag_name TEXT NOT NULL,
    tag_type TEXT NOT NULL,
    PRIMARY KEY (artwork_id, tag_id)
);

CREATE TABLE IF NOT EXISTS favorites (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    artwork_id TEXT NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, artwork_id)
);

CREATE TABLE IF NOT EXISTS artwork_views (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    artwork_id TEXT NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    viewed_on DATE NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, artwork_id, viewed_on)
);

-- The local administrator is the only seeded user. Artwork metadata and tags
-- are loaded from ARTWORK_CATALOG_PATH after migrations complete.
INSERT INTO users (id, email, created_at)
VALUES ('user-1', 'user@example.com', '2026-07-02T12:30:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_settings (user_id, selected_collections, notifications_enabled, notification_time, notification_time_zone)
VALUES ('user-1', ARRAY['russian', 'world'], TRUE, '20:00', 'Europe/Moscow')
ON CONFLICT (user_id) DO NOTHING;
