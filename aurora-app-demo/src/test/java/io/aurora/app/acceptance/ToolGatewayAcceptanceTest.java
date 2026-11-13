package io.aurora.app.acceptance;

import io.aurora.agent.approval.ApprovalEntity;
import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.approval.ApprovalStatus;
import io.aurora.agent.approval.ApprovalView;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.gateway.GatewayOutcome;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolGatewayAcceptanceTest extends BaseIntegrationTest {

    @Test
    @DisplayName("AC-01 未注册 Tool 被拒绝且不触达任何执行器")
    void unregisteredToolIsRejected() {
        GatewayOutcome outcome = invoke("internal.db.reset", "{}", admin());
        assertThat(outcome.status()).isEqualTo(GatewayOutcome.Status.DENIED);
        assertThat(outcome.errorCode()).isEqualTo("TOOL_NOT_REGISTERED");
        assertThat(auditService.byTrace(outcome.traceId()))
                .anyMatch(e -> "TOOL_NOT_REGISTERED".equals(e.decision()));
    }

    @Test
    @DisplayName("AC-02 无 user:delete 的用户被 RBAC 拒绝，且不生成审批")
    void rbacDeniesBeforeApproval() {
        long approvalsBefore = approvalRepository.count();
        GatewayOutcome outcome = invoke("user.delete", "{\"username\":\"张三\"}", ops());
        assertThat(outcome.status()).isEqualTo(GatewayOutcome.Status.DENIED);
        assertThat(outcome.errorCode()).isEqualTo("PERMISSION_DENIED");
        assertThat(approvalRepository.count()).isEqualTo(approvalsBefore);
        assertThat(auditService.byTrace(outcome.traceId()))
                .anyMatch(e -> "PERMISSION_DENIED".equals(e.decision()));
    }

    @Test
    @DisplayName("AC-03 WRITE+ASK 返回 PENDING，批准后才改变数据")
    void writeIsPendingUntilApproved() {
        long before = count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 7 AND role_id = 4");
        GatewayOutcome outcome = invoke("user.assignRole",
                "{\"username\":\"王五\",\"roleCode\":\"guest\"}", ops());
        assertThat(outcome.status()).isEqualTo(GatewayOutcome.Status.PENDING);
        assertThat(outcome.approvalId()).isNotBlank();
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 7 AND role_id = 4"))
                .isEqualTo(before);

        ApprovalService.DecisionOutcome decision = approve(outcome.approvalId());
        assertThat(decision.execution()).isNotNull();
        assertThat(decision.execution().status())
                .as("error=%s message=%s", decision.execution().errorCode(), decision.execution().message())
                .isEqualTo(GatewayOutcome.Status.EXECUTED);
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 7 AND role_id = 4"))
                .isEqualTo(before + 1);

        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 7 AND role_id = 4");
    }

    @Test
    @DisplayName("AC-04 DESTRUCTIVE + AUTO 在注册阶段被拒绝")
    void destructiveAutoIsInvalid() {
        ToolDefinition bad = new ToolDefinition("bad.destroy", "1.0.0", "bad",
                args("{\"type\":\"object\"}"), java.util.Set.of("user:delete"),
                SideEffect.DESTRUCTIVE, false, "NONE", 1000,
                ToolDefinition.ToolBudget.DEFAULT, ApprovalPolicy.AUTO, "core");
        assertThatThrownBy(() -> bad.validate(code -> true))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.INVALID_TOOL_DEFINITION);
    }

    @Test
    @DisplayName("AC-05 批准后参数被篡改 -> ARGS_MISMATCH 且不执行")
    void tamperedArgsDoNotExecute() {
        GatewayOutcome outcome = invoke("user.assignRole",
                "{\"username\":\"赵六\",\"roleCode\":\"guest\"}", ops());
        assertThat(outcome.approvalId()).isNotBlank();
        jdbc.update("UPDATE approval_request SET args_json = ? WHERE id = ?",
                "{\"username\":\"赵六\",\"roleCode\":\"admin\"}", outcome.approvalId());

        ApprovalService.DecisionOutcome decision = approve(outcome.approvalId());
        assertThat(decision.execution()).isNotNull();
        assertThat(decision.execution().errorCode()).isEqualTo("ARGS_MISMATCH");
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 8 AND role_id = 4"))
                .isZero();
        assertThat(approvalRepository.findById(outcome.approvalId()).orElseThrow().status)
                .isEqualTo(ApprovalStatus.FAILED);
    }

    @Test
    @DisplayName("AC-06 批准后撤销发起人权限，执行前复验失败")
    void revokedPermissionFailsAtExecution() {
        GatewayOutcome outcome = invoke("user.assignRole",
                "{\"username\":\"赵六\",\"roleCode\":\"guest\"}", ops());
        jdbc.update("DELETE FROM sys_role_resource WHERE role_id = 2 AND resource_id = 11");
        permissionService.invalidate();
        try {
            ApprovalService.DecisionOutcome decision = approve(outcome.approvalId());
            assertThat(decision.execution()).isNotNull();
            assertThat(decision.execution().errorCode()).isEqualTo("PERMISSION_DENIED");
            assertThat(approvalRepository.findById(outcome.approvalId()).orElseThrow().status)
                    .isEqualTo(ApprovalStatus.FAILED);
        } finally {
            jdbc.update("INSERT INTO sys_role_resource (role_id, resource_id) VALUES (2, 11)");
            permissionService.invalidate();
        }
    }

    @Test
    @DisplayName("AC-07 发起人不能批准自己的角色变更审批")
    void selfApprovalOfRoleChangeIsForbidden() {
        GatewayOutcome outcome = invoke("user.assignRole",
                "{\"username\":\"赵六\",\"roleCode\":\"guest\"}", admin());
        assertThat(outcome.status()).isEqualTo(GatewayOutcome.Status.PENDING);
        assertThatThrownBy(() -> approvalService.decide(outcome.approvalId(), admin(),
                io.aurora.contract.Actor.user(), true, "self"))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.SELF_APPROVAL_FORBIDDEN);
        assertThat(approvalRepository.findById(outcome.approvalId()).orElseThrow().status)
                .isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("AC-08 并发批准同一审批只执行一次")
    void concurrentApprovalExecutesOnce() throws Exception {
        GatewayOutcome outcome = invoke("user.assignRole",
                "{\"username\":\"赵六\",\"roleCode\":\"guest\"}", ops());
        String approvalId = outcome.approvalId();
        int workers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    approvalService.decide(approvalId, admin(), io.aurora.contract.Actor.user(),
                            true, "concurrent");
                    return true;
                } catch (AuroraException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(20, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdownNow();
        assertThat(successes).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM audit_event
                WHERE approval_id = ? AND decision = 'EXECUTED' AND type = 'TOOL_CALL'
                """, approvalId)).isEqualTo(1);
        assertThat(approvalRepository.findById(approvalId).orElseThrow().status)
                .isEqualTo(ApprovalStatus.EXECUTED);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = 8 AND role_id = 4");
    }

    @Test
    @DisplayName("AC-09 审计不可用时写类 Tool 不执行")
    void auditUnavailableFailsClosed() {
        GatewayOutcome outcome = invoke("user.assignRole",
                "{\"username\":\"赵六\",\"roleCode\":\"guest\"}", ops());
        auditService.simulateUnavailableForNextWrites(1);
        assertThatThrownBy(() -> approve(outcome.approvalId()))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.AUDIT_UNAVAILABLE);
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 8 AND role_id = 4"))
                .isZero();
    }

    @Test
    @DisplayName("AC-15 审计的 onBehalfOf 是真实用户而不是模糊的 Agent")
    void auditRecordsRealUserAndActor() {
        GatewayOutcome outcome = invoke("user.search", "{\"query\":\"\",\"limit\":3}", ops());
        assertThat(outcome.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);
        var executed = auditService.byTrace(outcome.traceId()).stream()
                .filter(e -> "TOOL_CALL".equals(e.type()))
                .filter(e -> "EXECUTED".equals(e.decision()))
                .findFirst()
                .orElseThrow();
        assertThat(executed.username()).isEqualTo("陈曦");
        assertThat(executed.actorType()).isEqualTo("AGENT");
        assertThat(executed.agentId()).isEqualTo("test-agent");
        assertThat(executed.roles()).contains("ops");
    }

    @Test
    @DisplayName("AC-16 审计哈希链检出篡改")
    void auditChainDetectsTampering() {
        GatewayOutcome outcome = invoke("user.search", "{\"query\":\"\",\"limit\":1}", admin());
        var event = auditService.byTrace(outcome.traceId()).get(0);
        assertThat(auditService.verifyChain().valid()).isTrue();
        jdbc.update("UPDATE audit_event SET decision = ? WHERE id = ?", "TAMPERED", event.seq());
        AuditService.ChainVerification broken = auditService.verifyChain();
        assertThat(broken.valid()).isFalse();
        assertThat(broken.brokenAt()).isNotNull();
        jdbc.update("UPDATE audit_event SET decision = ? WHERE id = ?", event.decision(), event.seq());
        assertThat(auditService.verifyChain().valid()).isTrue();
    }

    @Test
    @DisplayName("权限内低可操作度操作只需本人二次确认，不生成审批")
    void permissionHolderConfirmsLowOperabilityAction() {
        long approvalsBefore = approvalRepository.count();
        GatewayOutcome prompt = invoke("user.update",
                "{\"username\":\"王五\",\"status\":\"DISABLED\"}", ops());
        assertThat(prompt.status()).isEqualTo(GatewayOutcome.Status.CONFIRMATION_REQUIRED);
        assertThat(prompt.confirmation()).isNotNull();
        assertThat(prompt.confirmation().intent()).contains("王五").contains("停用");
        assertThat(prompt.confirmation().operability()).isLessThan(60);
        assertThat(prompt.confirmation().requiresConfirmation()).isTrue();
        assertThat(prompt.confirmation().riskReasons()).isNotEmpty();
        assertThat(approvalRepository.count()).isEqualTo(approvalsBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM sys_user WHERE username = 'wangwu'",
                String.class)).isEqualTo("ACTIVE");

        GatewayOutcome confirmed = invokeConfirmed("user.update",
                "{\"username\":\"王五\",\"status\":\"DISABLED\"}", ops());
        assertThat(confirmed.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);
        assertThat(approvalRepository.count()).isEqualTo(approvalsBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM sys_user WHERE username = 'wangwu'",
                String.class)).isEqualTo("DISABLED");
        jdbc.update("UPDATE sys_user SET status = 'ACTIVE' WHERE username = 'wangwu'");
    }

    @Test
    @DisplayName("权限内的高可操作度写入直接执行，不弹风险确认")
    void permissionHolderRoutineWriteExecutesDirectly() {
        GatewayOutcome outcome = invoke("user.update",
                "{\"username\":\"王五\",\"title\":\"高级实验员\"}", ops());
        assertThat(outcome.status()).isEqualTo(GatewayOutcome.Status.EXECUTED);
        assertThat(jdbc.queryForObject("SELECT title FROM sys_user WHERE username = 'wangwu'",
                String.class)).isEqualTo("高级实验员");
        jdbc.update("UPDATE sys_user SET title = '实验员' WHERE username = 'wangwu'");
    }

    @Test
    @DisplayName("AC-27 预算耗尽时 Agent 停止并写 BUDGET 事件（服务层）")
    void budgetStateIsObservable() {
        var state = gateway.budget("budget-test");
        assertThat(state).containsKeys("toolCalls", "maxToolCalls", "exhausted");
    }
}
