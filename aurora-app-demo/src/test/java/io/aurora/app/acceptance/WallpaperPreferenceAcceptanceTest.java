package io.aurora.app.acceptance;

import io.aurora.app.preference.DockPreferenceService;
import io.aurora.app.preference.WallpaperService;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WallpaperPreferenceAcceptanceTest extends BaseIntegrationTest {

    @Autowired WallpaperService wallpaperService;
    @Autowired DockPreferenceService dockPreferenceService;

    @Test
    @DisplayName("壁纸偏好默认使用极光预设并按用户隔离")
    void defaultWallpaperIsPresetAndScopedPerUser() {
        wallpaperService.reset(admin());
        var adminWallpaper = wallpaperService.get(admin());
        assertThat(adminWallpaper.kind()).isEqualTo("PRESET");
        assertThat(adminWallpaper.presetId()).isEqualTo("aurora");

        wallpaperService.setPreset(ops(), "ocean");
        assertThat(wallpaperService.get(ops()).presetId()).isEqualTo("ocean");
        assertThat(wallpaperService.get(admin()).presetId()).isEqualTo("aurora");

        wallpaperService.reset(ops());
    }

    @Test
    @DisplayName("预设壁纸写入 MySQL，重新读取仍然生效")
    void presetPersistsAcrossReads() {
        wallpaperService.setPreset(guest(), "graphite");
        assertThat(wallpaperService.get(guest()).kind()).isEqualTo("PRESET");
        assertThat(wallpaperService.get(guest()).presetId()).isEqualTo("graphite");
        assertThat(count("SELECT COUNT(*) FROM user_preference WHERE user_id = 4")).isEqualTo(1);

        wallpaperService.reset(guest());
        assertThat(wallpaperService.get(guest()).presetId()).isEqualTo("aurora");
    }

    @Test
    @DisplayName("未知预设被拒绝")
    void unknownPresetIsRejected() {
        assertThatThrownBy(() -> wallpaperService.setPreset(admin(), "unknown"))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("对象存储不可用时上传壁纸明确失败，不写入半成品偏好")
    void uploadFailsWhenStorageUnavailable() {
        MockMultipartFile image = new MockMultipartFile("file", "wall.jpg", "image/jpeg",
                new byte[]{1, 2, 3, 4});
        assertThatThrownBy(() -> wallpaperService.upload(admin(), image))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.TOOL_FAILED);
        assertThat(wallpaperService.get(admin()).kind()).isEqualTo("PRESET");
    }

    @Test
    @DisplayName("最近三张壁纸可切换，第四张会挤出最早的一张")
    void wallpaperHistoryKeepsThreeAndCanSwitchBack() {
        jdbc.update("DELETE FROM user_wallpaper_history WHERE user_id = 1");
        jdbc.update("DELETE FROM user_preference WHERE user_id = 1");

        wallpaperService.setPreset(admin(), "dawn");
        wallpaperService.setPreset(admin(), "ocean");
        wallpaperService.setPreset(admin(), "graphite");
        assertThat(wallpaperService.history(admin())).hasSize(3);

        wallpaperService.setPreset(admin(), "aurora");
        var history = wallpaperService.history(admin());
        assertThat(history).hasSize(3);
        assertThat(history).extracting(entry -> entry.presetId())
                .containsExactly("aurora", "graphite", "ocean");

        long oldest = history.get(2).id();
        var applied = wallpaperService.applyHistory(admin(), oldest);
        assertThat(applied.presetId()).isEqualTo("ocean");
        assertThat(wallpaperService.history(admin()).get(0).presetId()).isEqualTo("ocean");
    }

    @Test
    @DisplayName("程序坞顺序写入账号，访达固定首位且未知应用被过滤")
    void dockOrderPersistsPerAccount() {
        var saved = dockPreferenceService.save(2,
                List.of("agent", "finder", "directory", "not-an-app", "agent"));
        assertThat(saved).containsExactly("finder", "agent", "directory");
        assertThat(dockPreferenceService.get(2)).containsExactly("finder", "agent", "directory");
        assertThat(dockPreferenceService.get(3)).contains("finder");
    }
}
