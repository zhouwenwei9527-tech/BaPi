package io.aurora.app.acceptance;

import io.aurora.app.rbac.RbacAdminService;
import io.aurora.rbac.PermissionService;
import io.aurora.rbac.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RbacGovernanceAcceptanceTest extends BaseIntegrationTest {

    @Autowired RbacAdminService rbacAdminService;
    @Autowired PermissionService permissions;

    @Test
    @DisplayName("超级管理员拥有通配最高权限，并可编辑角色授权")
    void superAdminIsHighestAndCanEditPermissions() {
        Principal admin = admin();
        assertThat(admin.isSuperAdmin()).isTrue();
        assertThat(admin.can("any:future:permission")).isTrue();
        assertThat(ops().can("rbac:manage")).isFalse();

        long guestRoleId = 4L;
        assertThat(principalLoader.load(4, 4).can("knowledge:internal")).isFalse();
        rbacAdminService.togglePermission(guestRoleId, "knowledge:internal", true);
        permissions.invalidate();
        assertThat(principalLoader.load(4, 4).can("knowledge:internal")).isTrue();
        rbacAdminService.togglePermission(guestRoleId, "knowledge:internal", false);
        permissions.invalidate();
        assertThat(principalLoader.load(4, 4).can("knowledge:internal")).isFalse();
    }

    @Test
    @DisplayName("超级管理员可以新建角色、改数据范围、编辑菜单名称")
    void canCreateRoleAndRenameMenu() {
        String code = "temp_" + System.currentTimeMillis();
        var role = rbacAdminService.createRole(code, "临时角色", "测试", "DEPT");
        assertThat(role.scopeType()).isEqualTo("DEPT");
        assertThat(role.builtin()).isFalse();

        var renamed = rbacAdminService.updateMenu("file:read", "访达（测试改名）", "menu:workspace",
                1, "folder", "测试改名");
        assertThat(renamed.name()).isEqualTo("访达（测试改名）");
        rbacAdminService.updateMenu("file:read", "访达", "menu:workspace", 1, "folder",
                "对象浏览与上下文构造");

        List<RbacAdminService.MenuNode> tree = rbacAdminService.menuTree();
        assertThat(tree).isNotEmpty();
        assertThat(tree.stream().flatMap(node -> node.children().stream()))
                .anyMatch(node -> "file:read".equals(node.code()));

        jdbc.update("DELETE FROM sys_role_resource WHERE role_id = ?", role.id());
        jdbc.update("DELETE FROM sys_data_scope_rule WHERE role_id = ?", role.id());
        jdbc.update("DELETE FROM sys_role WHERE id = ?", role.id());
        permissions.invalidate();
    }
}
