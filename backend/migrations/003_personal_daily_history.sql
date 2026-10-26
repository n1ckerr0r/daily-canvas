ALTER TABLE artworks
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS daily_artwork_assignments (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_on DATE NOT NULL,
    artwork_id TEXT NOT NULL REFERENCES artworks(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, assigned_on),
    UNIQUE (user_id, artwork_id)
);

CREATE INDEX IF NOT EXISTS daily_artwork_assignments_user_date_idx
    ON daily_artwork_assignments(user_id, assigned_on DESC);

CREATE INDEX IF NOT EXISTS daily_artwork_assignments_artwork_idx
    ON daily_artwork_assignments(artwork_id);
