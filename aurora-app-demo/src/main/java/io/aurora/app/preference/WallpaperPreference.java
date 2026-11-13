package io.aurora.app.preference;

import java.time.Instant;

/**
 * Wallpaper metadata for one user. Image bytes never live in MySQL; only the
 * MinIO object key is stored here.
 */
public record WallpaperPreference(
        String kind,
        String presetId,
        String objectKey,
        String name,
        String contentType,
        Instant updatedAt
) {

    public boolean isImage() {
        return "IMAGE".equals(kind);
    }
}
