package io.aurora.app.preference;

import io.aurora.contract.ApiResponse;
import io.aurora.rbac.Principal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Personal desktop preferences. These endpoints only ever touch the caller's
 * own row, so no approval or elevated permission is required.
 */
@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final WallpaperService wallpaperService;
    private final DockPreferenceService dockPreferenceService;

    public PreferenceController(WallpaperService wallpaperService,
                                DockPreferenceService dockPreferenceService) {
        this.wallpaperService = wallpaperService;
        this.dockPreferenceService = dockPreferenceService;
    }

    @GetMapping("/wallpaper")
    public ApiResponse<WallpaperView> wallpaper(@RequestAttribute Principal principal) {
        return ApiResponse.ok(wallpaperService.get(principal));
    }

    @PutMapping("/wallpaper")
    public ApiResponse<WallpaperView> setPreset(@RequestAttribute Principal principal,
                                                @RequestBody PresetRequest request) {
        return ApiResponse.ok(wallpaperService.setPreset(principal,
                request == null ? null : request.presetId()));
    }

    @PostMapping(value = "/wallpaper", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WallpaperView> upload(@RequestAttribute Principal principal,
                                             @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(wallpaperService.upload(principal, file));
    }

    @DeleteMapping("/wallpaper")
    public ApiResponse<WallpaperView> reset(@RequestAttribute Principal principal) {
        return ApiResponse.ok(wallpaperService.reset(principal));
    }

    @GetMapping("/wallpaper/history")
    public ApiResponse<List<WallpaperHistoryView>> history(
            @RequestAttribute Principal principal) {
        return ApiResponse.ok(wallpaperService.history(principal));
    }

    @PostMapping("/wallpaper/history/{id}")
    public ApiResponse<WallpaperView> applyHistory(@RequestAttribute Principal principal,
                                                   @PathVariable long id) {
        return ApiResponse.ok(wallpaperService.applyHistory(principal, id));
    }

    @GetMapping("/dock")
    public ApiResponse<DockView> dock(@RequestAttribute Principal principal) {
        return ApiResponse.ok(new DockView(dockPreferenceService.get(principal.userId())));
    }

    @PutMapping("/dock")
    public ApiResponse<DockView> saveDock(@RequestAttribute Principal principal,
                                          @RequestBody DockRequest request) {
        return ApiResponse.ok(new DockView(dockPreferenceService.save(principal.userId(),
                request == null ? List.of() : request.apps())));
    }

    public record PresetRequest(String presetId) {
    }

    public record DockRequest(List<String> apps) {
    }

    public record DockView(List<String> apps) {
    }
}
