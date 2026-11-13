package io.aurora.app.acceptance;

import io.aurora.app.permission.PermissionRequestService;
import io.aurora.contract.Actor;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionRequestAcceptanceTest extends BaseIntegrationTest {

    @Autowired PermissionRequestService service;

    private Principal auditor() {
        return principalLoader.load(10, 5);
    }

    @Test
    @DisplayName("用户申请角色 -> 其他角色审批 -> 角色到账")
    void userRequestsAndAnotherRoleApproves() {
        long before = count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 4 AND role_id = 2");
        assertThat(before).isZero();
        var request = service.create(guest(), "ops", "需要参与运营任务");
        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(service.list(admin()).stream().map(PermissionRequestService.PermissionRequestView::id))
                .contains(request.id());

        var outcome = service.decide(request.id(), auditor(), true, "批准权限申请");
        assertThat(outcome.request().status()).isEqualTo("APPROVED");
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 4 AND role_id = 2"))
                .isEqualTo(1);

        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 4 AND role_id = 2");
    }

    @Test
    @DisplayName("不能申请管理员/安全审计员角色，也不能自批")
    void requestGuardrails() {
        assertThat(service.requestableRoles(guest()))
                .noneMatch(role -> role.code().equals("admin") || role.code().equals("security"));
        assertThatThrownBy(() -> service.create(guest(), "admin", "提权"))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.PERMISSION_DENIED);

        String code = "selfrole_" + System.currentTimeMillis();
        var role = ((io.aurora.app.rbac.RbacAdminService) applicationContext.getBean("rbacAdminService"))
                .createRole(code, "自批测试", "test", "SELF");
        try {
            var request = service.create(auditor(), code, "测试自批");
            assertThatThrownBy(() -> service.decide(request.id(), auditor(), true, "自批"))
                    .isInstanceOf(AuroraException.class)
                    .extracting(e -> ((AuroraException) e).code())
                    .isEqualTo(AuroraErrorCode.SELF_APPROVAL_FORBIDDEN);
            jdbc.update("DELETE FROM permission_request WHERE id = ?", request.id());
        } finally {
            jdbc.update("DELETE FROM sys_role_resource WHERE role_id = ?", role.id());
            jdbc.update("DELETE FROM sys_data_scope_rule WHERE role_id = ?", role.id());
            jdbc.update("DELETE FROM sys_role WHERE id = ?", role.id());
        }
    }

    @Test
    @DisplayName("分页：用户查询返回总数与当前页切片")
    void userPagination() {
        var first = userQueryService.searchPage(admin(), "", 0, 3, true);
        var second = userQueryService.searchPage(admin(), "", 1, 3, true);
        assertThat(first.total()).isGreaterThanOrEqualTo(10);
        assertThat(first.items()).hasSize(3);
        assertThat(first.totalPages()).isGreaterThanOrEqualTo(3);
        assertThat(first.items()).doesNotContainAnyElementsOf(second.items());
    }

    @Autowired io.aurora.rbac.UserQueryService userQueryService;
    @Autowired org.springframework.context.ApplicationContext applicationContext;
}
