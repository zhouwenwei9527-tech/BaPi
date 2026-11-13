package io.aurora.app.preference;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import io.aurora.retrieval.StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side wallpaper preference: preset ids and metadata in the business
 * database, uploaded images in object storage. Changing a wallpaper is a
 * personal preference and never creates an approval; it only requires an
 * authenticated session. The three most recent wallpapers stay switchable.
 */
@Service
public class WallpaperService {

    private static final String DEFAULT_PRESET = "aurora";
    private static final int HISTORY_LIMIT = 3;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final Duration URL_TTL = Duration.ofDays(7);
    private static final Map<String, String> PRESETS = presets();
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final UserPreferenceRepository repository;
    private final StoragePort storage;

    public WallpaperService(UserPreferenceRepository repository, StoragePort storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public WallpaperView get(Principal principal) {
        WallpaperPreference preference = repository.findWallpaper(principal.userId())
                .orElseGet(this::defaultPreference);
        if (!preference.isImage()
                && (preference.presetId() == null || !PRESETS.containsKey(preference.presetId()))) {
            preference = defaultPreference();
        }
        return toView(preference);
    }

    public List<WallpaperHistoryView> history(Principal principal) {
        return repository.findHistory(principal.userId(), HISTORY_LIMIT).stream()
                .map(entry -> toHistoryView(entry.id(), entry.wallpaper()))
                .toList();
    }

    public WallpaperView setPreset(Principal principal, String presetId) {
        if (presetId == null || !PRESETS.containsKey(presetId)) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "未知壁纸预设：" + presetId);
        }
        WallpaperPreference next = new WallpaperPreference("PRESET", presetId, null,
                PRESETS.get(presetId), null, Instant.now());
        repository.saveWallpaper(principal.userId(), next);
        recordHistory(principal.userId(), next);
        return toView(next);
    }

    public WallpaperView upload(Principal principal, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "壁纸图片不能超过 8MB");
        }
        String originalName = file.getOriginalFilename() == null
                ? "wallpaper.jpg" : file.getOriginalFilename();
        String safeName = originalName.replaceAll(".*[/\\\\]", "")
                .replaceAll("[^a-zA-Z0-9._\\u4e00-\\u9fa5-]", "_");
        String extension = safeName.contains(".")
                ? safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "壁纸仅支持 JPG、PNG 或 WebP 图片");
        }
        if (!storage.available()) {
            throw new AuroraException(AuroraErrorCode.TOOL_FAILED, "壁纸服务暂时不可用，请稍后重试");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "无法读取上传的图片", e);
        }
        String key = "wallpapers/" + principal.userId() + "/" + UUID.randomUUID() + "." + extension;
        if (!storage.put(key, bytes, CONTENT_TYPES.get(extension))) {
            throw new AuroraException(AuroraErrorCode.TOOL_FAILED, "壁纸保存失败，请稍后重试");
        }

        WallpaperPreference next = new WallpaperPreference("IMAGE", null, key,
                truncate(safeName, 160), CONTENT_TYPES.get(extension), Instant.now());
        try {
            repository.saveWallpaper(principal.userId(), next);
        } catch (RuntimeException saveFailure) {
            storage.delete(key);
            throw saveFailure;
        }
        recordHistory(principal.userId(), next);
        return toView(next);
    }

    public WallpaperView applyHistory(Principal principal, long historyId) {
        WallpaperHistoryEntry entry = repository.findHistoryEntry(principal.userId(), historyId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND,
                        "历史壁纸不存在或已过期"));
        WallpaperPreference source = entry.wallpaper();
        WallpaperPreference next = new WallpaperPreference(source.kind(), source.presetId(),
                source.objectKey(), source.name(), source.contentType(), Instant.now());
        repository.saveWallpaper(principal.userId(), next);
        recordHistory(principal.userId(), next);
        return toView(next);
    }

    public WallpaperView reset(Principal principal) {
        WallpaperPreference next = defaultPreference();
        repository.saveWallpaper(principal.userId(), next);
        recordHistory(principal.userId(), next);
        return toView(next);
    }

    private void recordHistory(long userId, WallpaperPreference wallpaper) {
        repository.deleteHistoryByWallpaper(userId, wallpaper);
        repository.insertHistory(userId, wallpaper);
        List<String> prunedKeys = repository.pruneHistory(userId, HISTORY_LIMIT);
        if (prunedKeys.isEmpty()) {
            return;
        }
        Set<String> stillReferenced = new HashSet<>();
        repository.findWallpaper(userId)
                .map(WallpaperPreference::objectKey)
                .ifPresent(stillReferenced::add);
        repository.findHistory(userId, HISTORY_LIMIT).stream()
                .map(entry -> entry.wallpaper().objectKey())
                .filter(key -> key != null && !key.isBlank())
                .forEach(stillReferenced::add);
        prunedKeys.stream()
                .filter(key -> !stillReferenced.contains(key))
                .forEach(storage::delete);
    }

    private WallpaperView toView(WallpaperPreference preference) {
        if (!preference.isImage()) {
            return new WallpaperView("PRESET", preference.presetId(), preference.name(), null,
                    storage.provider(), preference.updatedAt() == null
                    ? null : preference.updatedAt().toString(), storage.available());
        }
        String url = storage.presign(preference.objectKey(), URL_TTL);
        return new WallpaperView("IMAGE", null, preference.name(), url, storage.provider(),
                preference.updatedAt() == null ? null : preference.updatedAt().toString(),
                storage.available());
    }

    private WallpaperHistoryView toHistoryView(long id, WallpaperPreference preference) {
        String url = preference.isImage()
                ? storage.presign(preference.objectKey(), URL_TTL) : null;
        return new WallpaperHistoryView(id, preference.kind(), preference.presetId(),
                preference.name(), url, preference.updatedAt() == null
                ? null : preference.updatedAt().toString());
    }

    private WallpaperPreference defaultPreference() {
        return new WallpaperPreference("PRESET", DEFAULT_PRESET, null,
                PRESETS.get(DEFAULT_PRESET), null, null);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static Map<String, String> presets() {
        Map<String, String> presets = new LinkedHashMap<>();
        presets.put("aurora", "极光");
        presets.put("dawn", "晨雾");
        presets.put("ocean", "深海");
        presets.put("graphite", "石墨");
        return Map.copyOf(presets);
    }
}
