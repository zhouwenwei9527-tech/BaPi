package io.aurora.app.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.approval.ApprovalRepository;
import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.gateway.GatewayOutcome;
import io.aurora.agent.gateway.InvocationRequest;
import io.aurora.agent.gateway.ToolInvocationGateway;
import io.aurora.agent.trace.TraceService;
import io.aurora.app.tool.ToolBootstrap;
import io.aurora.contract.Actor;
import io.aurora.contract.Json;
import io.aurora.rbac.PermissionService;
import io.aurora.rbac.Principal;
import io.aurora.rbac.PrincipalLoader;
import io.aurora.retrieval.VectorIndexer;
import io.aurora.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Autowired protected ToolBootstrap toolBootstrap;
    @Autowired protected ToolRegistry toolRegistry;
    @Autowired protected ToolInvocationGateway gateway;
    @Autowired protected ApprovalService approvalService;
    @Autowired protected ApprovalRepository approvalRepository;
    @Autowired protected AuditService auditService;
    @Autowired protected TraceService traceService;
    @Autowired protected PrincipalLoader principalLoader;
    @Autowired protected PermissionService permissionService;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected VectorIndexer vectorIndexer;

    @BeforeEach
    void baseSetup() {
        if (toolRegistry.size() == 0) {
            toolBootstrap.run(null);
        }
        vectorIndexer.reindexAll();
        permissionService.invalidate();
    }

    protected Principal admin() {
        return principalLoader.load(1, 1);
    }

    protected Principal ops() {
        return principalLoader.load(2, 2);
    }

    protected Principal finance() {
        return principalLoader.load(3, 3);
    }

    protected Principal guest() {
        return principalLoader.load(4, 4);
    }

    protected GatewayOutcome invoke(String tool, String argsJson, Principal principal) {
        return gateway.invoke(new InvocationRequest(tool, Json.parse(argsJson), principal,
                Actor.agent("test-agent"), "test-" + UUID.randomUUID(),
                "tr-" + UUID.randomUUID(), UUID.randomUUID().toString()));
    }

    protected GatewayOutcome invokeAsUser(String tool, String argsJson, Principal principal) {
        return gateway.invoke(new InvocationRequest(tool, Json.parse(argsJson), principal,
                Actor.user(), "test-" + UUID.randomUUID(),
                "tr-" + UUID.randomUUID(), UUID.randomUUID().toString()));
    }

    protected GatewayOutcome invokeConfirmed(String tool, String argsJson, Principal principal) {
        return gateway.invoke(new InvocationRequest(tool, Json.parse(argsJson), principal,
                Actor.user(), "test-" + UUID.randomUUID(),
                "tr-" + UUID.randomUUID(), UUID.randomUUID().toString(), true));
    }

    protected ApprovalService.DecisionOutcome approve(String id) {
        return approvalService.decide(id, admin(), Actor.user(), true, "test approval");
    }

    protected long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    protected JsonNode args(String json) {
        return Json.parse(json);
    }
}
