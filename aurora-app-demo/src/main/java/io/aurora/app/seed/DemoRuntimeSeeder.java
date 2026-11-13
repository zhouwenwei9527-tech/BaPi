package io.aurora.app.seed;

import io.aurora.agent.audit.AuditService;
import io.aurora.agent.gateway.GatewayOutcome;
import io.aurora.agent.gateway.InvocationRequest;
import io.aurora.agent.gateway.ToolInvocationGateway;
import io.aurora.contract.Actor;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.rbac.PrincipalLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Produces a small, realistic runtime trail for the demo UI: one executed
 * read, one retrieval, and one pending approval requested by 陈曦 (ops).
 * Skipped in tests and once audit data already exists.
 */
@Component
@Order(30)
public class DemoRuntimeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRuntimeSeeder.class);

    private final boolean enabled;
    private final PrincipalLoader principalLoader;
    private final ToolInvocationGateway gateway;
    private final AuditService auditService;

    public DemoRuntimeSeeder(@Value("${aurora.demo.seed-runtime:false}") boolean enabled,
                             PrincipalLoader principalLoader, ToolInvocationGateway gateway,
                             AuditService auditService) {
        this.enabled = enabled;
        this.principalLoader = principalLoader;
        this.gateway = gateway;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || auditService.count() > 0) {
            return;
        }
        try {
            Principal ops = principalLoader.load(2, 2);
            GatewayOutcome search = gateway.invoke(new InvocationRequest("user.search",
                    Json.parse("{\"query\":\"\",\"limit\":6}"), ops,
                    Actor.agent("aurora-agent"), "demo-bootstrap", "tr-demo-search", null));
            GatewayOutcome knowledge = gateway.invoke(new InvocationRequest("knowledge.search",
                    Json.parse("{\"query\":\"数据范围在 Tool 层如何传递\",\"limit\":4}"), ops,
                    Actor.agent("aurora-agent"), "demo-bootstrap", "tr-demo-knowledge", null));
            GatewayOutcome pending = gateway.invoke(new InvocationRequest("file.share",
                    Json.parse("{\"docId\":\"d1\",\"to\":\"partner@lab.example\",\"expiresHours\":24}"),
                    ops, Actor.agent("aurora-agent"), "demo-bootstrap", "tr-demo-approval", null));
            log.info("Demo trail seeded: search={}, knowledge={}, approval={}",
                    search.status(), knowledge.status(), pending.status());
        } catch (RuntimeException e) {
            log.warn("Demo trail seeding skipped: {}", e.getMessage());
        }
    }
}
