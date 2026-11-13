-- Wallpaper history (up to 3 entries per user) and account-level dock order.
ALTER TABLE user_preference ADD COLUMN dock_apps VARCHAR(1000);

CREATE TABLE IF NOT EXISTS user_wallpaper_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    wallpaper_kind VARCHAR(16) NOT NULL,
    wallpaper_preset VARCHAR(64),
    wallpaper_object_key VARCHAR(255),
    wallpaper_name VARCHAR(160),
    wallpaper_content_type VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallpaper_history_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_wallpaper_history_user ON user_wallpaper_history (user_id, created_at);
