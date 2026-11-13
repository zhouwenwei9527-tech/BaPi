-- Per-user desktop preferences. Wallpaper metadata lives in MySQL while the
-- image bytes live in MinIO, so preferences follow the account across devices.
CREATE TABLE IF NOT EXISTS user_preference (
    user_id BIGINT PRIMARY KEY,
    wallpaper_kind VARCHAR(16) NOT NULL DEFAULT 'PRESET',
    wallpaper_preset VARCHAR(64),
    wallpaper_object_key VARCHAR(255),
    wallpaper_name VARCHAR(160),
    wallpaper_content_type VARCHAR(80),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
);
