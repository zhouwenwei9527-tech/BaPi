package io.aurora.app.preference;

public record WallpaperHistoryView(
        long id,
        String kind,
        String presetId,
        String name,
        String url,
        String storedAt
) {
}
