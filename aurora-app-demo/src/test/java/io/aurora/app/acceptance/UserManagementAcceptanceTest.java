package io.aurora.app.acceptance;

import io.aurora.agent.gateway.GatewayOutcome;
import io.aurora.contract.Actor;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import io.aurora.rbac.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserManagementAcceptanceTest extends BaseIntegrationTest {

    @Autowired UserQueryService userQueryService;

    private Principal auditor() {
        return principalLoader.load(10, 5);
    }

    @Test
    @DisplayName("数据入口：有权限的用户创建后直接落库并可查询")
    void createUserDirectly() {
        String username = "crud" + System.currentTimeMillis();
        GatewayOutcome created = invoke("user.create", """
                {"username":"%s","displayName":"测试成员","title":"实验员","deptId":5,
                 "password":"test1234","roleCode":"ops"}
                """.formatted(username), admin());
        assertThat(created.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);
        assertThat(count("SELECT COUNT(*) FROM sys_user WHERE username = ?", username)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM sys_user_role ur
                JOIN sys_user u ON u.id = ur.user_id
                JOIN sys_role r ON r.id = ur.role_id
                WHERE u.username = ? AND r.code = 'ops'
                """, username)).isEqualTo(1);
        assertThat(userQueryService.search(admin(), username, 10, true))
                .anyMatch(u -> u.username().equals(username));

        jdbc.update("DELETE FROM sys_user_role WHERE user_id = (SELECT id FROM sys_user WHERE username = ?)", username);
        jdbc.update("DELETE FROM sys_user WHERE username = ?", username);
    }

    @Test
    @DisplayName("本人资料直接生效，不需要审批")
    void selfProfileUpdateIsDirect() {
        var changed = userQueryService.updateOwnProfile(admin(), "林深测试", "系统管理员");
        assertThat(changed.displayName()).isEqualTo("林深测试");
        assertThat(jdbc.queryForObject("SELECT display_name FROM sys_user WHERE username = 'admin'",
                String.class)).isEqualTo("林深测试");
        userQueryService.updateOwnProfile(admin(), "林深", "系统管理员");
    }

    @Test
    @DisplayName("数据入口：用户编辑由本人确认风险后直接生效")
    void updateUserDirectlyAfterConfirmation() {
        String username = "edit" + System.currentTimeMillis();
        GatewayOutcome created = invoke("user.create", """
                {"username":"%s","displayName":"待编辑","title":"成员","deptId":5,
                 "password":"test1234","roleCode":"ops"}
                """.formatted(username), admin());
        assertThat(created.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);

        GatewayOutcome prompt = invoke("user.update", """
                {"username":"%s","displayName":"已编辑","title":"高级实验员","status":"DISABLED"}
                """.formatted(username), admin());
        assertThat(prompt.status()).isEqualTo(GatewayOutcome.Status.CONFIRMATION_REQUIRED);
        assertThat(prompt.confirmation()).isNotNull();
        GatewayOutcome executed = invokeConfirmed("user.update", """
                {"username":"%s","displayName":"已编辑","title":"高级实验员","status":"DISABLED"}
                """.formatted(username), admin());
        assertThat(executed.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);
        assertThat(jdbc.queryForObject(
                "SELECT display_name FROM sys_user WHERE username = ?", String.class, username))
                .isEqualTo("已编辑");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sys_user WHERE username = ?", String.class, username))
                .isEqualTo("DISABLED");

        jdbc.update("DELETE FROM sys_user_role WHERE user_id = (SELECT id FROM sys_user WHERE username = ?)", username);
        jdbc.update("DELETE FROM sys_user WHERE username = ?", username);
    }

    @Test
    @DisplayName("数据入口：用户删除由本人二次确认后移除")
    void deleteUserAfterConfirmation() {
        String username = "del" + System.currentTimeMillis();
        GatewayOutcome created = invoke("user.create", """
                {"username":"%s","displayName":"待删除","title":"成员","deptId":5,
                 "password":"test1234","roleCode":"guest"}
                """.formatted(username), admin());
        assertThat(created.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);

        GatewayOutcome prompt = invoke("user.delete", "{\"username\":\"" + username + "\"}", admin());
        assertThat(prompt.status()).isEqualTo(GatewayOutcome.Status.CONFIRMATION_REQUIRED);
        assertThat(count("SELECT COUNT(*) FROM sys_user WHERE username = ?", username)).isEqualTo(1);
        GatewayOutcome executed = invokeConfirmed("user.delete",
                "{\"username\":\"" + username + "\"}", admin());
        assertThat(executed.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);
        assertThat(count("SELECT COUNT(*) FROM sys_user WHERE username = ?", username)).isZero();
    }

    @Test
    @DisplayName("权限护栏：不能授予超出自身权限的角色")
    void cannotGrantRoleAboveOwnPermissions() {
        String username = "esc" + System.currentTimeMillis();
        GatewayOutcome created = invoke("user.create", """
                {"username":"%s","displayName":"越权测试","title":"成员","deptId":5,
                 "password":"test1234","roleCode":"ops"}
                """.formatted(username), admin());
        assertThat(created.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);
        assertThatThrownBy(() -> userQueryService.assignRole(ops(), username, "admin"))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.PERMISSION_DENIED);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = (SELECT id FROM sys_user WHERE username = ?)", username);
        jdbc.update("DELETE FROM sys_user WHERE username = ?", username);
    }
}
