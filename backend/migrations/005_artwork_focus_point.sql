ALTER TABLE artworks
    ADD COLUMN IF NOT EXISTS focus_x DOUBLE PRECISION NOT NULL DEFAULT 0.5
        CHECK (focus_x >= 0 AND focus_x <= 1),
    ADD COLUMN IF NOT EXISTS focus_y DOUBLE PRECISION NOT NULL DEFAULT 0.5
        CHECK (focus_y >= 0 AND focus_y <= 1);
