package io.aurora.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.budget.BudgetService;
import io.aurora.agent.gateway.GatewayOutcome;
import io.aurora.agent.gateway.InvocationRequest;
import io.aurora.agent.gateway.ToolInvocationGateway;
import io.aurora.agent.model.ModelPort;
import io.aurora.agent.session.AgentMemoryEntity;
import io.aurora.agent.session.AgentMemoryRepository;
import io.aurora.agent.session.AgentMessageEntity;
import io.aurora.agent.session.AgentMessageRepository;
import io.aurora.agent.session.AgentSessionEntity;
import io.aurora.agent.session.AgentSessionRepository;
import io.aurora.agent.session.ModelCallEntity;
import io.aurora.agent.session.ModelCallRepository;
import io.aurora.agent.session.ToolCallRecordEntity;
import io.aurora.agent.session.ToolCallRecordRepository;
import io.aurora.agent.trace.TraceService;
import io.aurora.contract.Actor;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent loop: model proposes, Gateway disposes. Budget, tool-call records and
 * model call recordings live here (PRD §8, AC-14, AC-27).
 */
@Service
public class AgentService {

    private static final int MAX_LOOP_STEPS = 6;

    private final ModelPort modelPort;
    private final ToolInvocationGateway gateway;
    private final ToolRegistry toolRegistry;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final TraceService traceService;
    private final BudgetService budgetService;
    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final ToolCallRecordRepository toolCallRepository;
    private final AgentMemoryRepository memoryRepository;
    private final ModelCallRepository modelCallRepository;

    public AgentService(ModelPort modelPort,
                        ToolInvocationGateway gateway,
                        ToolRegistry toolRegistry,
                        ApprovalService approvalService,
                        AuditService auditService,
                        TraceService traceService,
                        BudgetService budgetService,
                        AgentSessionRepository sessionRepository,
                        AgentMessageRepository messageRepository,
                        ToolCallRecordRepository toolCallRepository,
                        AgentMemoryRepository memoryRepository,
                        ModelCallRepository modelCallRepository) {
        this.modelPort = modelPort;
        this.gateway = gateway;
        this.toolRegistry = toolRegistry;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.traceService = traceService;
        this.budgetService = budgetService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.toolCallRepository = toolCallRepository;
        this.memoryRepository = memoryRepository;
        this.modelCallRepository = modelCallRepository;
    }

    public AgentReply chat(Principal principal, String sessionId, String message) {
        return chat(principal, sessionId, message, false);
    }

    public AgentReply chat(Principal principal, String sessionId, String message,
                           boolean confirmed) {
        if (!principal.can("agent:use")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 agent:use 权限");
        }
        if (message == null || message.isBlank()) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "消息不能为空");
        }
        AgentSessionEntity session = ensureSession(principal, sessionId, message);
        String traceId = "tr-" + UUID.randomUUID();
        saveMessage(session.id, "user", message, null);

        List<AgentStep> steps = new ArrayList<>();
        List<ModelPort.ToolSummary> tools = toolRegistry.list().stream()
                .filter(ToolRegistry.ToolView::active)
                .map(t -> new ModelPort.ToolSummary(t.name(), t.description(),
                        null, t.sideEffect().name()))
                .toList();

        String lastToolName = null;
        String lastToolSummary = null;
        JsonNode lastToolData = null;
        int toolCalls = 0;
        int tokens = 0;
        int loop = 0;

        while (loop++ < MAX_LOOP_STEPS) {
            try {
                budgetService.check(session.id);
            } catch (AuroraException e) {
                auditBudget(traceId, session.id, principal, e.code().name(), e.getMessage());
                steps.add(new AgentStep("预算", "bad", e.getMessage()));
                String reply = "已停止：\n" + e.getMessage() + "。本次已完成 " + toolCalls + " 次 Tool 调用。";
                saveMessage(session.id, "assistant", reply, null);
                touch(session);
                return new AgentReply(session.id, traceId, reply,
                        e.code() == AuroraErrorCode.CIRCUIT_OPEN ? "CIRCUIT_OPEN" : "BUDGET_EXCEEDED",
                        null, e.code().name(), steps, toolCalls, budgetService.state(session.id),
                        suggestions(), null);
            }

            ModelPort.ModelRequest request = new ModelPort.ModelRequest(message, tools,
                    history(session.id), lastToolName, lastToolSummary, lastToolData);
            ModelPort.ModelTurn turn = modelPort.complete(request);
            tokens += turn.tokensUsed();
            budgetService.recordTokens(session.id, turn.tokensUsed());
            recordModelCall(session.id, traceId, loop, request, turn, principal);

            if (turn.toolCall() == null) {
                String reply = turn.text() == null || turn.text().isBlank() ? "操作完成。" : turn.text();
                saveMessage(session.id, "assistant", reply, null);
                touch(session);
                steps.add(new AgentStep("模型", "ok", "生成最终回答（" + tokens + " tokens）"));
                return new AgentReply(session.id, traceId, reply, "OK", null, null, steps,
                        toolCalls, budgetService.state(session.id), suggestions(), null);
            }

            toolCalls++;
            ModelPort.ToolCall call = turn.toolCall();
            steps.add(new AgentStep("计划", "ok", "模型提出调用 " + call.name()));
            GatewayOutcome outcome = gateway.invoke(new InvocationRequest(call.name(), call.args(),
                    principal, Actor.agent("aurora-agent"), session.id, traceId,
                    "session:" + session.id + ":" + traceId + ":" + toolCalls, confirmed));
            steps.addAll(outcome.steps().stream()
                    .map(s -> new AgentStep(s.stage(), s.status(), s.detail()))
                    .toList());
            recordToolCall(session.id, principal, traceId, call, outcome);
            saveMessage(session.id, "assistant", turn.text() == null ? "" : turn.text(),
                    Json.write(Map.of("tool", call.name(), "args", call.args(),
                            "status", outcome.status().name(),
                            "approvalId", outcome.approvalId() == null ? "" : outcome.approvalId())));

            switch (outcome.status()) {
                case PENDING -> {
                    String reply = "这项「" + call.name() + "」是写操作，已挂起为审批单 "
                            + shortId(outcome.approvalId()) + "。批准后我才会执行，业务数据现在没有变化。";
                    saveMessage(session.id, "assistant", reply, null);
                    touch(session);
                    return new AgentReply(session.id, traceId, reply, "PENDING",
                            outcome.approvalId(), null, steps, toolCalls,
                            budgetService.state(session.id), suggestions(), null);
                }
                case CONFIRMATION_REQUIRED -> {
                    var confirmation = outcome.confirmation();
                    String reply = "「" + call.name() + "」的可操作度较低，需要你确认风险后才会执行。"
                            + "确认后只影响你自己的权限范围，不需要其他人再审批。";
                    saveMessage(session.id, "assistant", reply, null);
                    touch(session);
                    return new AgentReply(session.id, traceId, reply, "CONFIRMATION_REQUIRED",
                            null, null, steps, toolCalls, budgetService.state(session.id),
                            suggestions(), confirmation == null ? null : new ConfirmationView(
                            call.name(), confirmation.intent(), confirmation.operability(),
                            confirmation.riskLevel(), confirmation.riskReasons()));
                }
                case DENIED, FAILED -> {
                    String reply = "请求被拒绝：" + outcome.errorCode() + "\n" + outcome.message()
                            + "\n\nAgent 的权限不会超过你本人，所有调用都经过 ToolInvocationGateway。";
                    saveMessage(session.id, "assistant", reply, null);
                    touch(session);
                    return new AgentReply(session.id, traceId, reply, "DENIED",
                            null, outcome.errorCode(), steps, toolCalls,
                            budgetService.state(session.id), suggestions(), null);
                }
                case EXECUTED -> {
                    lastToolName = call.name();
                    lastToolSummary = outcome.summary();
                    lastToolData = outcome.data();
                }
            }
        }
        String reply = "已达到本轮工具调用上限，先返回当前进展。";
        auditBudget(traceId, session.id, principal, "BUDGET_EXCEEDED", reply);
        saveMessage(session.id, "assistant", reply, null);
        touch(session);
        return new AgentReply(session.id, traceId, reply, "BUDGET_EXCEEDED", null, null, steps,
                toolCalls, budgetService.state(session.id), suggestions(), null);
    }

    public List<SessionView> sessions(Principal principal) {
        return sessionRepository.findAllByUserIdOrderByUpdatedAtDesc(principal.userId()).stream()
                .map(s -> new SessionView(s.id, s.title, s.createdAt.toString(),
                        s.updatedAt.toString(), messageCount(s.id)))
                .toList();
    }

    public SessionView createSession(Principal principal, String title) {
        AgentSessionEntity session = new AgentSessionEntity();
        session.id = UUID.randomUUID().toString();
        session.userId = principal.userId();
        session.title = title == null || title.isBlank() ? "新会话" : truncate(title, 60);
        sessionRepository.save(session);
        return new SessionView(session.id, session.title, session.createdAt.toString(),
                session.updatedAt.toString(), 0);
    }

    public List<MessageView> messages(Principal principal, String sessionId) {
        requireSession(principal, sessionId);
        return messageRepository.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(m -> new MessageView(m.id, m.role, m.content, m.toolCallsJson, m.createdAt.toString()))
                .toList();
    }

    public ReplayView replay(Principal principal, String traceId) {
        List<TraceService.TraceStep> trace = traceService.timeline(traceId);
        List<AuditService.AuditEventView> audit = auditService.byTrace(traceId);
        List<ModelCallView> calls = modelCallRepository.findByTraceIdOrderByIdAsc(traceId).stream()
                .map(m -> new ModelCallView(m.seq, m.provider, m.createdAt.toString()))
                .toList();
        List<String> toolSequence = audit.stream()
                .filter(e -> "TOOL_CALL".equals(e.type()))
                .map(AuditService.AuditEventView::toolName)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ReplayView(traceId, trace, audit, calls, toolSequence);
    }

    public ReplayView replayForUser(Principal principal, String traceId) {
        List<io.aurora.agent.session.ModelCallEntity> calls =
                modelCallRepository.findByTraceIdOrderByIdAsc(traceId);
        boolean owns = calls.stream().anyMatch(c -> sessionRepository
                .findByIdAndUserId(c.sessionId, principal.userId()).isPresent());
        if (!owns && !principal.can("audit:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "无权回放该轨迹");
        }
        return replay(principal, traceId);
    }

    public List<MemoryView> memories(Principal principal) {
        return memoryRepository.findByUserIdOrderByIdDesc(principal.userId()).stream()
                .map(m -> new MemoryView(m.id, m.memoryKey, m.memoryValue, m.createdAt.toString()))
                .toList();
    }

    public boolean deleteMemory(Principal principal, long id) {
        return memoryRepository.deleteByIdAndUserId(id, principal.userId()) > 0;
    }

    public List<ToolCallView> recentCalls(Principal principal) {
        return toolCallRepository.findTop20ByUserIdOrderByIdDesc(principal.userId()).stream()
                .map(t -> new ToolCallView(t.id, t.sessionId, t.traceId, t.toolName, t.decision,
                        t.approvalId, t.summary, t.status, t.createdAt.toString()))
                .toList();
    }

    private AgentSessionEntity ensureSession(Principal principal, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            AgentSessionEntity session = new AgentSessionEntity();
            session.id = UUID.randomUUID().toString();
            session.userId = principal.userId();
            session.title = truncate(message, 40);
            return sessionRepository.save(session);
        }
        return requireSession(principal, sessionId);
    }

    private AgentSessionEntity requireSession(Principal principal, String sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, principal.userId())
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "会话不存在"));
    }

    private void saveMessage(String sessionId, String role, String content, String toolCallsJson) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.sessionId = sessionId;
        message.role = role;
        message.content = content == null ? "" : content;
        message.toolCallsJson = toolCallsJson;
        messageRepository.save(message);
    }

    private void touch(AgentSessionEntity session) {
        session.updatedAt = Instant.now();
        sessionRepository.save(session);
    }

    private List<String> history(String sessionId) {
        List<AgentMessageEntity> messages = messageRepository.findBySessionIdOrderByIdAsc(sessionId);
        int from = Math.max(0, messages.size() - 12);
        return messages.subList(from, messages.size()).stream()
                .map(m -> m.role + ": " + m.content)
                .toList();
    }

    private long messageCount(String sessionId) {
        return messageRepository.findBySessionIdOrderByIdAsc(sessionId).size();
    }

    private void recordToolCall(String sessionId, Principal principal, String traceId,
                                ModelPort.ToolCall call, GatewayOutcome outcome) {
        ToolCallRecordEntity record = new ToolCallRecordEntity();
        record.sessionId = sessionId;
        record.userId = principal.userId();
        record.traceId = traceId;
        record.toolName = call.name();
        record.argsDigest = Json.argsHash(call.args());
        record.decision = outcome.errorCode() == null ? outcome.status().name() : outcome.errorCode();
        record.approvalId = outcome.approvalId();
        record.summary = truncate(outcome.summary() == null ? outcome.message() : outcome.summary(), 500);
        record.status = outcome.status().name();
        toolCallRepository.save(record);
    }

    private void recordModelCall(String sessionId, String traceId, int seq,
                                 ModelPort.ModelRequest request, ModelPort.ModelTurn turn,
                                 Principal principal) {
        ModelCallEntity entity = new ModelCallEntity();
        entity.sessionId = sessionId;
        entity.traceId = traceId;
        entity.seq = seq;
        entity.provider = modelPort.provider();
        entity.requestJson = Json.write(Map.of(
                "message", request.userMessage(),
                "tools", request.tools(),
                "history", request.history(),
                "lastTool", request.lastToolName() == null ? "" : request.lastToolName()));
        entity.responseJson = Json.write(Map.of(
                "text", turn.text() == null ? "" : turn.text(),
                "toolCall", turn.toolCall() == null ? "" : turn.toolCall(),
                "tokens", turn.tokensUsed()));
        modelCallRepository.save(entity);
        try {
            auditService.record(AuditDraft.of(AuditService.TYPE_MODEL_CALL)
                    .trace(traceId).session(sessionId).actor(Actor.agent("aurora-agent"))
                    .onBehalfOf(principal).tool(null)
                    .decision(modelPort.provider()).status("OK")
                    .latency(turn.tokensUsed()));
        } catch (AuroraException e) {
            // Model call audit failure does not authorize anything; gateway still guards effects.
        }
    }

    private void auditBudget(String traceId, String sessionId, Principal principal,
                             String code, String detail) {
        try {
            auditService.record(AuditDraft.of(AuditService.TYPE_BUDGET)
                    .trace(traceId).session(sessionId).actor(Actor.agent("aurora-agent"))
                    .onBehalfOf(principal).decision(code).status("ERROR")
                    .refs(List.of(Map.of("objectType", "session", "objectId", sessionId,
                            "detail", detail == null ? "" : detail))));
        } catch (AuroraException ignored) {
            // Budget stop is already returned to the caller.
        }
    }

    private List<String> suggestions() {
        return List.of("查询本月新增用户", "把李四加入运营组", "删除用户 张三",
                "检索数据范围在 Tool 层如何传递", "直接调用 /internal/db/reset");
    }

    private String shortId(String id) {
        return id == null ? "" : id.length() > 8 ? id.substring(0, 8) : id;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public record AgentStep(String label, String status, String detail) {
    }

    public record AgentReply(String sessionId, String traceId, String message, String status,
                             String approvalId, String errorCode, List<AgentStep> steps,
                             int toolCalls, BudgetService.BudgetState budget,
                             List<String> suggestions, ConfirmationView confirmation) {
    }

    public record ConfirmationView(String toolName, String intent, int operability,
                                   String riskLevel, List<String> riskReasons) {
    }

    public record SessionView(String id, String title, String createdAt, String updatedAt,
                              long messages) {
    }

    public record MessageView(Long id, String role, String content, String toolCallsJson,
                              String createdAt) {
    }

    public record MemoryView(Long id, String key, String value, String createdAt) {
    }

    public record ToolCallView(Long id, String sessionId, String traceId, String toolName,
                               String decision, String approvalId, String summary, String status,
                               String createdAt) {
    }

    public record ModelCallView(int seq, String provider, String createdAt) {
    }

    public record ReplayView(String traceId, List<TraceService.TraceStep> trace,
                             List<AuditService.AuditEventView> audit,
                             List<ModelCallView> modelCalls, List<String> toolSequence) {
    }
}
