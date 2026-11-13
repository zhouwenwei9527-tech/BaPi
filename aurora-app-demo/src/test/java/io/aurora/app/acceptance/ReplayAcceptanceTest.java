package io.aurora.app.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.AgentService;
import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.budget.BudgetService;
import io.aurora.agent.gateway.ToolInvocationGateway;
import io.aurora.agent.model.ModelPort;
import io.aurora.agent.model.ReplayModelPort;
import io.aurora.agent.session.AgentMemoryRepository;
import io.aurora.agent.session.AgentMessageRepository;
import io.aurora.agent.session.AgentSessionRepository;
import io.aurora.agent.session.ModelCallRepository;
import io.aurora.agent.session.ToolCallRecordRepository;
import io.aurora.agent.trace.TraceService;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.tool.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayAcceptanceTest extends BaseIntegrationTest {

    @Autowired AgentSessionRepository sessionRepository;
    @Autowired AgentMessageRepository messageRepository;
    @Autowired ToolCallRecordRepository toolCallRepository;
    @Autowired AgentMemoryRepository memoryRepository;
    @Autowired ModelCallRepository modelCallRepository;
    @Autowired BudgetService budgetService;

    private AgentService service(ModelPort port) {
        return new AgentService(port, gateway, toolRegistry, approvalService, auditService,
                traceService, budgetService, sessionRepository, messageRepository,
                toolCallRepository, memoryRepository, modelCallRepository);
    }

    private List<String> toolSequence(String traceId) {
        return auditService.byTrace(traceId).stream()
                .filter(e -> "TOOL_CALL".equals(e.type()))
                .map(AuditService.AuditEventView::toolName)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Test
    @DisplayName("AC-14 录制模型输出可确定性重放同一 Tool 决策序列")
    void recordedModelTurnsReplayDeterministically() {
        ScriptedModelPort scripted = new ScriptedModelPort(List.of(
                ModelPort.ModelTurn.call("user.search",
                        Json.parse("{\"query\":\"张三\",\"limit\":5}"), 12),
                ModelPort.ModelTurn.text("查询完成。", 6)));
        AgentService live = service(scripted);
        Principal admin = admin();
        AgentService.AgentReply first = live.chat(admin, null, "查询张三");
        List<String> originalSequence = toolSequence(first.traceId());
        assertThat(originalSequence).contains("user.search");

        AgentService replayed = service(new ReplayModelPort(scripted.emittedTurns()));
        AgentService.AgentReply second = replayed.chat(admin, null, "查询张三");
        assertThat(toolSequence(second.traceId())).isEqualTo(originalSequence);

        var replay = live.replay(admin, first.traceId());
        assertThat(replay.trace()).isNotEmpty();
        assertThat(replay.toolSequence()).contains("user.search");
    }

    static final class ScriptedModelPort implements ModelPort {
        private final List<ModelTurn> turns;
        private final List<ModelTurn> emitted = new ArrayList<>();
        private int index;

        ScriptedModelPort(List<ModelTurn> turns) {
            this.turns = turns;
        }

        @Override
        public String provider() {
            return "scripted";
        }

        @Override
        public ModelTurn complete(ModelRequest request) {
            ModelTurn turn = turns.get(Math.min(index, turns.size() - 1));
            index++;
            emitted.add(turn);
            return turn;
        }

        List<ModelTurn> emittedTurns() {
            return List.copyOf(emitted);
        }
    }
}
