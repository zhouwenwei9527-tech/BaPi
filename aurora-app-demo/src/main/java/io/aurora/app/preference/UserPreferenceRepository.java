package io.aurora.app.preference;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UserPreferenceRepository {

    private static final RowMapper<WallpaperPreference> WALLPAPER_MAPPER = (rs, rowNum) ->
            new WallpaperPreference(
                    rs.getString("wallpaper_kind"),
                    rs.getString("wallpaper_preset"),
                    rs.getString("wallpaper_object_key"),
                    rs.getString("wallpaper_name"),
                    rs.getString("wallpaper_content_type"),
                    rs.getTimestamp("updated_at") == null
                            ? null : rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<WallpaperHistoryEntry> HISTORY_MAPPER = (rs, rowNum) ->
            new WallpaperHistoryEntry(rs.getLong("id"), new WallpaperPreference(
                    rs.getString("wallpaper_kind"),
                    rs.getString("wallpaper_preset"),
                    rs.getString("wallpaper_object_key"),
                    rs.getString("wallpaper_name"),
                    rs.getString("wallpaper_content_type"),
                    rs.getTimestamp("created_at") == null
                            ? null : rs.getTimestamp("created_at").toInstant()));

    private final JdbcTemplate jdbc;

    public UserPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<WallpaperPreference> findWallpaper(long userId) {
        List<WallpaperPreference> rows = jdbc.query("""
                SELECT wallpaper_kind, wallpaper_preset, wallpaper_object_key,
                       wallpaper_name, wallpaper_content_type, updated_at
                FROM user_preference WHERE user_id = ?
                """, WALLPAPER_MAPPER, userId);
        return rows.stream().findFirst();
    }

    public void saveWallpaper(long userId, WallpaperPreference wallpaper) {
        Timestamp now = Timestamp.from(wallpaper.updatedAt() == null
                ? Instant.now() : wallpaper.updatedAt());
        int updated = jdbc.update("""
                UPDATE user_preference
                SET wallpaper_kind = ?, wallpaper_preset = ?, wallpaper_object_key = ?,
                    wallpaper_name = ?, wallpaper_content_type = ?, updated_at = ?
                WHERE user_id = ?
                """, wallpaper.kind(), wallpaper.presetId(), wallpaper.objectKey(),
                wallpaper.name(), wallpaper.contentType(), now, userId);
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO user_preference (user_id, wallpaper_kind, wallpaper_preset,
                        wallpaper_object_key, wallpaper_name, wallpaper_content_type, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, userId, wallpaper.kind(), wallpaper.presetId(), wallpaper.objectKey(),
                    wallpaper.name(), wallpaper.contentType(), now);
        } catch (DuplicateKeyException concurrentInsert) {
            jdbc.update("""
                    UPDATE user_preference
                    SET wallpaper_kind = ?, wallpaper_preset = ?, wallpaper_object_key = ?,
                        wallpaper_name = ?, wallpaper_content_type = ?, updated_at = ?
                    WHERE user_id = ?
                    """, wallpaper.kind(), wallpaper.presetId(), wallpaper.objectKey(),
                    wallpaper.name(), wallpaper.contentType(), now, userId);
        }
    }

    public Optional<String> findDockApps(long userId) {
        List<String> rows = jdbc.queryForList(
                "SELECT dock_apps FROM user_preference WHERE user_id = ?", String.class, userId);
        return rows.stream().filter(value -> value != null && !value.isBlank()).findFirst();
    }

    public void saveDockApps(long userId, String dockApps) {
        int updated = jdbc.update("UPDATE user_preference SET dock_apps = ? WHERE user_id = ?",
                dockApps, userId);
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update("INSERT INTO user_preference (user_id, dock_apps) VALUES (?, ?)",
                    userId, dockApps);
        } catch (DuplicateKeyException concurrentInsert) {
            jdbc.update("UPDATE user_preference SET dock_apps = ? WHERE user_id = ?",
                    dockApps, userId);
        }
    }

    public List<WallpaperHistoryEntry> findHistory(long userId, int limit) {
        return jdbc.query("""
                SELECT id, wallpaper_kind, wallpaper_preset, wallpaper_object_key,
                       wallpaper_name, wallpaper_content_type, created_at
                FROM user_wallpaper_history
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, HISTORY_MAPPER, userId, limit);
    }

    public Optional<WallpaperHistoryEntry> findHistoryEntry(long userId, long id) {
        return jdbc.query("""
                SELECT id, wallpaper_kind, wallpaper_preset, wallpaper_object_key,
                       wallpaper_name, wallpaper_content_type, created_at
                FROM user_wallpaper_history
                WHERE user_id = ? AND id = ?
                """, HISTORY_MAPPER, userId, id).stream().findFirst();
    }

    public void deleteHistoryEntry(long userId, long id) {
        jdbc.update("DELETE FROM user_wallpaper_history WHERE user_id = ? AND id = ?", userId, id);
    }

    public void deleteHistoryByWallpaper(long userId, WallpaperPreference wallpaper) {
        jdbc.update("""
                DELETE FROM user_wallpaper_history
                WHERE user_id = ? AND wallpaper_kind = ?
                  AND COALESCE(wallpaper_preset, '') = ?
                  AND COALESCE(wallpaper_object_key, '') = ?
                """, userId, wallpaper.kind(),
                wallpaper.presetId() == null ? "" : wallpaper.presetId(),
                wallpaper.objectKey() == null ? "" : wallpaper.objectKey());
    }

    public long insertHistory(long userId, WallpaperPreference wallpaper) {
        Timestamp now = Timestamp.from(wallpaper.updatedAt() == null
                ? Instant.now() : wallpaper.updatedAt());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO user_wallpaper_history (user_id, wallpaper_kind, wallpaper_preset,
                        wallpaper_object_key, wallpaper_name, wallpaper_content_type, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, wallpaper.kind());
            statement.setString(3, wallpaper.presetId());
            statement.setString(4, wallpaper.objectKey());
            statement.setString(5, wallpaper.name());
            statement.setString(6, wallpaper.contentType());
            statement.setTimestamp(7, now);
            return statement;
        }, keyHolder);
        java.util.Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        // H2 returns the id together with DEFAULT columns; MySQL exposes a
        // single generated key. Prefer the explicit id when present.
        Object value = keys.containsKey("id")
                ? keys.get("id") : keys.values().iterator().next();
        return value instanceof Number number ? number.longValue() : 0;
    }

    /** Removes history rows beyond the newest {@code keep} and returns their object keys. */
    public List<String> pruneHistory(long userId, int keep) {
        List<WallpaperHistoryEntry> all = jdbc.query("""
                SELECT id, wallpaper_kind, wallpaper_preset, wallpaper_object_key,
                       wallpaper_name, wallpaper_content_type, created_at
                FROM user_wallpaper_history
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                """, HISTORY_MAPPER, userId);
        return all.stream().skip(keep).map(entry -> {
            deleteHistoryEntry(userId, entry.id());
            return entry.wallpaper().objectKey();
        }).filter(key -> key != null && !key.isBlank()).toList();
    }
}
