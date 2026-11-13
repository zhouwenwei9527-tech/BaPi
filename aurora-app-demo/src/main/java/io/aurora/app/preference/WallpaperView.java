package io.aurora.app.preference;

/**
 * Wallpaper as seen by the client. Image URLs are short-lived MinIO presigned
 * links; the client refreshes them whenever it restores a session.
 */
public record WallpaperView(
        String kind,
        String presetId,
        String name,
        String url,
        String provider,
        String storedAt,
        boolean storageAvailable
) {
}
