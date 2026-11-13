package io.aurora.app.acceptance;

import io.aurora.agent.AgentService;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.model.ModelPort;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.rbac.PrincipalLoader;
import io.aurora.tool.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "aurora.agent.budget.maxToolCalls=1")
@Import(BudgetAcceptanceTest.Config.class)
class BudgetAcceptanceTest {

    @Autowired AgentService agentService;
    @Autowired AuditService auditService;
    @Autowired PrincipalLoader principalLoader;
    @Autowired ToolRegistry toolRegistry;
    @Autowired io.aurora.app.tool.ToolBootstrap toolBootstrap;

    @Test
    @DisplayName("AC-27 超过 maxToolCalls 时 Agent 停止、返回进展并写 BUDGET 事件")
    void budgetStopsAgent() {
        if (toolRegistry.size() == 0) toolBootstrap.run(null);
        Principal admin = principalLoader.load(1, 1);
        AgentService.AgentReply reply = agentService.chat(admin, null, "重复查询");
        assertThat(reply.status()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(auditService.byTrace(reply.traceId()))
                .anyMatch(e -> "BUDGET".equals(e.type()));
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ModelPort loopingModel() {
            return new ModelPort() {
                @Override
                public String provider() {
                    return "looping-stub";
                }

                @Override
                public ModelTurn complete(ModelRequest request) {
                    return ModelTurn.call("user.search",
                            Json.parse("{\"query\":\"\",\"limit\":1}"), 5);
                }
            };
        }
    }
}
