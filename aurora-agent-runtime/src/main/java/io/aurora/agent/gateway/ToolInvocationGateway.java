package io.aurora.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.approval.ApprovalEntity;
import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.budget.BudgetService;
import io.aurora.agent.trace.TraceService;
import io.aurora.contract.Actor;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.ApprovalPresentation;
import io.aurora.tool.SideEffect;
import io.aurora.tool.SimpleSchemaValidator;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolExecutor;
import io.aurora.tool.ToolRegistry;
import io.aurora.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The single execution path for every Tool (INV-1). The pipeline order in
 * PRD §5.3 is fixed and every exit writes an audit event.
 *
 * This is the only class allowed to call ToolExecutor (AC-25).
 */
@Service
public class ToolInvocationGateway {

    private static final Logger log = LoggerFactory.getLogger(ToolInvocationGateway.class);

    private final ToolRegistry registry;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final TraceService traceService;
    private final BudgetService budgetService;
    private final ExecutorService executorPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "aurora-tool-exec");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, GatewayOutcome> idempotencyStore = new ConcurrentHashMap<>();

    public ToolInvocationGateway(ToolRegistry registry,
                                 ApprovalService approvalService,
                                 AuditService auditService,
                                 TraceService traceService,
                                 BudgetService budgetService) {
        this.registry = registry;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.traceService = traceService;
        this.budgetService = budgetService;
    }

    public GatewayOutcome invoke(InvocationRequest request) {
        String traceId = request.traceId() == null || request.traceId().isBlank()
                ? "tr-" + UUID.randomUUID() : request.traceId();
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "adhoc-" + UUID.randomUUID() : request.sessionId();
        List<GatewayOutcome.PipelineStep> steps = new ArrayList<>();
        JsonNode args = request.args();
        String digest = Json.argsHash(args);
        long start = System.currentTimeMillis();

        // Step 1: registry lookup.
        Optional<ToolRegistry.RegisteredTool> found = registry.find(request.toolName());
        if (found.isEmpty()) {
            steps.add(step(traceId, "注册表", "bad", "未注册：" + request.toolName()));
            return deny(AuroraErrorCode.TOOL_NOT_REGISTERED,
                    "这个能力没有注册为 Tool，Agent 不能调用它", request, traceId, sessionId, digest, steps);
        }
        ToolRegistry.RegisteredTool registered = found.get();
        ToolDefinition definition = registered.definition();
        steps.add(step(traceId, "注册表", "ok", "Tool 已注册：" + definition.name()));

        // Step 2: active state.
        if (!registered.active()) {
            steps.add(step(traceId, "激活状态", "bad", "Tool 未激活：" + definition.name()));
            return deny(AuroraErrorCode.TOOL_NOT_ACTIVE, "Tool 当前未激活", request, traceId, sessionId,
                    digest, steps);
        }
        steps.add(step(traceId, "激活状态", "ok", "Tool 已激活"));

        // Step 3: RBAC before schema (no schema probing by unauthorized users).
        Principal principal = request.principal();
        for (String permission : definition.requiredPermissions()) {
            if (!principal.can(permission)) {
                steps.add(step(traceId, "权限校验", "bad", "缺少权限：" + permission));
                return deny(AuroraErrorCode.PERMISSION_DENIED,
                        "你没有这项权限，Agent 也不会获得它", request, traceId, sessionId, digest, steps);
            }
        }
        steps.add(step(traceId, "权限校验", "ok",
                definition.requiredPermissions().isEmpty() ? "无需额外权限"
                        : "通过：" + String.join(", ", definition.requiredPermissions())));

        // Step 4: schema.
        List<String> errors = SimpleSchemaValidator.errors(definition.inputSchema(), args);
        if (!errors.isEmpty()) {
            steps.add(step(traceId, "参数校验", "bad", String.join("；", errors)));
            return deny(AuroraErrorCode.SCHEMA_INVALID, "参数不合法：" + String.join("；", errors),
                    request, traceId, sessionId, digest, steps);
        }
        steps.add(step(traceId, "参数校验", "ok", "inputSchema 校验通过"));

        // Step 5: budget + circuit.
        try {
            budgetService.check(sessionId);
        } catch (AuroraException e) {
            steps.add(step(traceId, "预算", "bad", e.getMessage()));
            AuditDraft budgetEvent = AuditDraft.of(AuditService.TYPE_BUDGET)
                    .trace(traceId).session(sessionId).actor(request.actor())
                    .onBehalfOf(principal).tool(definition.name()).argsDigest(digest)
                    .decision(e.code().name()).status("ERROR");
            safeAudit(budgetEvent);
            return deny(e.code(), e.getMessage(), request, traceId, sessionId, digest, steps);
        }
        budgetService.consumeCall(sessionId);
        steps.add(step(traceId, "预算", "ok", "会话预算可用"));

        // Step 6: DataScope is already resolved into Principal.
        steps.add(step(traceId, "数据范围", "ok", describeScope(principal)));

        // Step 7: policy decision.
        if (definition.approvalPolicy() == ApprovalPolicy.DENY_BY_DEFAULT) {
            steps.add(step(traceId, "审批策略", "bad", "DENY_BY_DEFAULT"));
            return deny(AuroraErrorCode.DENIED_BY_POLICY, "该 Tool 对所有调用者默认拒绝",
                    request, traceId, sessionId, digest, steps);
        }
        if (definition.approvalPolicy() == ApprovalPolicy.AUTO) {
            steps.add(step(traceId, "审批策略", "ok", "AUTO，直接执行"));
            return execute(definition, registered.executor(), request, traceId, sessionId, digest,
                    steps, start, null);
        }

        ApprovalPresentation presentation = describe(registered.executor(), principal, request.actor(),
                traceId, sessionId, args);

        // Permission holders execute directly; only low-operability actions ask
        // the caller to acknowledge the risk once. No second person is needed.
        if (definition.approvalPolicy() == ApprovalPolicy.SELF_CONFIRM) {
            if (presentation.requiresConfirmation() && !request.confirmed()) {
                steps.add(step(traceId, "确认策略", "warn",
                        "可操作度 " + presentation.operability() + "%，需要本人二次确认"));
                AuditDraft confirmation = AuditDraft.of(AuditService.TYPE_TOOL_CALL)
                        .trace(traceId).session(sessionId).actor(request.actor())
                        .onBehalfOf(principal).tool(definition.name()).argsDigest(digest)
                        .decision("CONFIRMATION_REQUIRED").status("OK");
                Long auditSeq = safeAuditId(confirmation);
                if (auditSeq == null) {
                    return GatewayOutcome.failed(AuroraErrorCode.AUDIT_UNAVAILABLE.name(),
                            "审计不可用，操作未执行", traceId, sessionId, null, steps);
                }
                return GatewayOutcome.confirmationRequired(presentation, traceId, sessionId,
                        auditSeq, steps);
            }
            steps.add(step(traceId, "确认策略", "ok", request.confirmed()
                    ? "本人已确认风险，直接执行"
                    : "可操作度较高，直接执行"));
            return execute(definition, registered.executor(), request, traceId, sessionId, digest,
                    steps, start, null);
        }

        // ASK keeps dual control for privilege-changing operations.
        String summary = summarizeArgs(args);
        String affected = affectedObjects(args);
        int affectedCount = args.has("limit") ? Math.min(args.path("limit").asInt(1), 100) : 1;
        ApprovalEntity approval = approvalService.createPending(definition, principal, request.actor(),
                args, summary, affected, affectedCount, sessionId, traceId, presentation);
        steps.add(step(traceId, "审批策略", "warn",
                definition.sideEffect() + " · ASK（双人复核），已挂起为审批 " + shortId(approval.id)));
        AuditDraft pending = AuditDraft.of(AuditService.TYPE_TOOL_CALL)
                .trace(traceId).session(sessionId).actor(request.actor())
                .onBehalfOf(principal).tool(definition.name()).argsDigest(digest)
                .decision("PENDING").approval(approval.id).status("OK");
        Long auditSeq = safeAuditId(pending);
        if (auditSeq == null) {
            return GatewayOutcome.failed(AuroraErrorCode.AUDIT_UNAVAILABLE.name(),
                    "审计不可用，写操作未执行", traceId, sessionId, null, steps);
        }
        steps.add(step(traceId, "审计", "ok", "已记录审批挂起事件"));
        return GatewayOutcome.pending(approval.id, traceId, sessionId, auditSeq, steps);
    }

    /**
     * Executes an approval that has already passed decision + re-validation.
     * Package-visible to ApprovalService but still the only executor caller.
     */
    public GatewayOutcome executeApproved(ToolDefinition definition, Principal requester, Actor actor,
                                          JsonNode args, String approvalId, String traceId,
                                          String sessionId) {
        List<GatewayOutcome.PipelineStep> steps = new ArrayList<>();
        String digest = Json.argsHash(args);
        long start = System.currentTimeMillis();
        if (!registry.isActive(definition.name())) {
            steps.add(step(traceId, "复验", "bad", "Tool 已停用"));
            return GatewayOutcome.failed(AuroraErrorCode.TOOL_NOT_ACTIVE.name(),
                    "Tool 已停用", traceId, sessionId, null, steps);
        }
        for (String permission : definition.requiredPermissions()) {
            if (!requester.can(permission)) {
                steps.add(step(traceId, "复验", "bad", "发起人权限已撤销：" + permission));
                return GatewayOutcome.failed(AuroraErrorCode.PERMISSION_DENIED.name(),
                        "发起人权限已撤销", traceId, sessionId, null, steps);
            }
        }
        steps.add(step(traceId, "复验", "ok", "Tool 激活、发起人权限、参数哈希均通过"));
        ToolExecutor executor = registry.find(definition.name())
                .map(ToolRegistry.RegisteredTool::executor)
                .orElseThrow();
        return execute(definition, executor, new InvocationRequest(definition.name(), args, requester,
                actor, sessionId, traceId, "approval:" + approvalId), traceId, sessionId, digest,
                steps, start, approvalId);
    }

    private GatewayOutcome execute(ToolDefinition definition, ToolExecutor executor,
                                   InvocationRequest request, String traceId, String sessionId,
                                   String digest, List<GatewayOutcome.PipelineStep> steps,
                                   long start, String approvalId) {
        String idempotencyKey = request.idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            GatewayOutcome cached = idempotencyStore.get(definition.name() + "|" + idempotencyKey);
            if (cached != null) {
                steps.add(step(traceId, "幂等", "ok", "命中幂等键，未重复执行"));
                return cached;
            }
        }

        // Fail closed: writes must have a successful pre-execution audit event.
        Long preAudit = null;
        if (definition.sideEffect() != SideEffect.READ) {
            AuditDraft attempt = AuditDraft.of(AuditService.TYPE_TOOL_CALL)
                    .trace(traceId).session(sessionId).actor(request.actor())
                    .onBehalfOf(request.principal()).tool(definition.name()).argsDigest(digest)
                    .decision("EXECUTING").approval(approvalId).status("OK");
            preAudit = safeAuditId(attempt);
            if (preAudit == null) {
                steps.add(step(traceId, "审计", "bad", "写操作前审计失败，已中止"));
                return GatewayOutcome.failed(AuroraErrorCode.AUDIT_UNAVAILABLE.name(),
                        "审计存储不可用，写操作未执行", traceId, sessionId, null, steps);
            }
            steps.add(step(traceId, "审计", "ok", "写操作前审计已落盘"));
        }

        ToolExecutionContext context = new ToolExecutionContext(request.principal(), request.actor(),
                traceId, sessionId, idempotencyKey, approvalId);
        ToolResult result;
        long toolStart = System.currentTimeMillis();
        Future<ToolResult> future = executorPool.submit(() -> executor.execute(context, request.args()));
        try {
            result = future.get(definition.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            budgetService.recordFailure(sessionId);
            String message = "Tool 执行超时（" + definition.timeoutMs() + "ms）";
            steps.add(step(traceId, "执行", "bad", message));
            auditOutcome(traceId, sessionId, definition, request, digest, approvalId,
                    "TOOL_TIMEOUT", "ERROR", System.currentTimeMillis() - toolStart);
            return GatewayOutcome.failed(AuroraErrorCode.TOOL_TIMEOUT.name(), message, traceId,
                    sessionId, preAudit, steps);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            AuroraErrorCode code = cause instanceof AuroraException ae
                    ? ae.code() : AuroraErrorCode.TOOL_FAILED;
            String message = cause.getMessage() == null ? "Tool 执行失败" : cause.getMessage();
            budgetService.recordFailure(sessionId);
            steps.add(step(traceId, "执行", "bad", message));
            auditOutcome(traceId, sessionId, definition, request, digest, approvalId,
                    code.name(), "ERROR", System.currentTimeMillis() - toolStart);
            return GatewayOutcome.failed(code.name(), message, traceId, sessionId, preAudit, steps);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            budgetService.recordFailure(sessionId);
            return GatewayOutcome.failed(AuroraErrorCode.TOOL_FAILED.name(), "执行被中断",
                    traceId, sessionId, preAudit, steps);
        }

        long latency = System.currentTimeMillis() - start;
        budgetService.recordSuccess(sessionId);
        steps.add(step(traceId, "执行", "ok",
                "完成于 " + latency + "ms，影响 " + result.affectedCount() + " 条"));
        Long auditSeq = auditOutcome(traceId, sessionId, definition, request, digest, approvalId,
                "EXECUTED", "OK", System.currentTimeMillis() - toolStart);
        if (auditSeq == null && definition.sideEffect() != SideEffect.READ) {
            // Pre-audit already persisted; the side effect is recorded. Surface a warning.
            steps.add(step(traceId, "审计", "warn", "结果审计写入失败，但执行前审计已存在"));
        } else {
            steps.add(step(traceId, "审计", "ok", "审计事件已追加"));
        }
        GatewayOutcome outcome = GatewayOutcome.executed(result.data(), result.summary(),
                result.affectedCount(), traceId, sessionId, auditSeq, latency, steps);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.put(definition.name() + "|" + idempotencyKey, outcome);
        }
        return outcome;
    }

    private ApprovalPresentation describe(ToolExecutor executor, Principal principal, Actor actor,
                                           String traceId, String sessionId, JsonNode args) {
        ToolExecutionContext context = new ToolExecutionContext(principal, actor, traceId,
                sessionId, null, null);
        try {
            return executor.describe(context, args);
        } catch (RuntimeException e) {
            return ApprovalPresentation.generic();
        }
    }

    private GatewayOutcome deny(AuroraErrorCode code, String message, InvocationRequest request,
                                String traceId, String sessionId, String digest,
                                List<GatewayOutcome.PipelineStep> steps) {
        AuditDraft draft = AuditDraft.of(AuditService.TYPE_TOOL_CALL)
                .trace(traceId).session(sessionId).actor(request.actor())
                .onBehalfOf(request.principal()).tool(request.toolName()).argsDigest(digest)
                .decision(code.name()).status("ERROR");
        Long auditSeq = safeAuditId(draft);
        if (auditSeq == null) {
            steps.add(step(traceId, "审计", "bad", "审计不可用"));
            return GatewayOutcome.failed(AuroraErrorCode.AUDIT_UNAVAILABLE.name(),
                    "审计存储不可用，请求已中止", traceId, sessionId, null, steps);
        }
        steps.add(step(traceId, "审计", "ok", "已记录拒绝事件"));
        log.info("Tool denied tool={} code={} user={}", request.toolName(), code,
                request.principal().username());
        return GatewayOutcome.denied(code.name(), message, traceId, sessionId, auditSeq, steps);
    }

    private Long auditOutcome(String traceId, String sessionId, ToolDefinition definition,
                              InvocationRequest request, String digest, String approvalId,
                              String decision, String status, long latency) {
        AuditDraft draft = AuditDraft.of(AuditService.TYPE_TOOL_CALL)
                .trace(traceId).session(sessionId).actor(request.actor())
                .onBehalfOf(request.principal()).tool(definition.name()).argsDigest(digest)
                .decision(decision).approval(approvalId).latency(Math.max(0, latency)).status(status);
        return safeAuditId(draft);
    }

    private Long safeAuditId(AuditDraft draft) {
        try {
            return auditService.record(draft).id();
        } catch (AuroraException e) {
            log.error("Audit write failed: {}", e.getMessage());
            return null;
        }
    }

    private void safeAudit(AuditDraft draft) {
        try {
            auditService.record(draft);
        } catch (AuroraException e) {
            log.error("Audit write failed: {}", e.getMessage());
        }
    }

    private GatewayOutcome.PipelineStep step(String traceId, String stage, String status, String detail) {
        traceService.append(traceId, stage, status, "bad".equals(status) ? "ERROR" : "OK",
                Map.of("detail", detail));
        return new GatewayOutcome.PipelineStep(stage, status, detail);
    }

    private String describeScope(Principal principal) {
        if (principal.dataScope().isAll()) {
            return "ALL · 全部门";
        }
        if (principal.dataScope().isSelfOnly()) {
            return "SELF · 仅本人";
        }
        return principal.dataScope().type() + " · 部门 " + principal.dataScope().deptIds();
    }

    private String summarizeArgs(JsonNode args) {
        if (args == null || args.isEmpty()) {
            return "无参数";
        }
        List<String> parts = new ArrayList<>();
        Set<String> sensitive = new LinkedHashSet<>(List.of("password", "token", "secret", "apikey",
                "api_key", "authorization"));
        args.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String value = sensitive.contains(key.toLowerCase(Locale.ROOT))
                    ? "***" : entry.getValue().asText();
            if (value.length() > 60) {
                value = value.substring(0, 57) + "…";
            }
            parts.add(key + "=" + value);
        });
        return String.join(", ", parts);
    }

    private String affectedObjects(JsonNode args) {
        for (String key : List.of("username", "file", "filename", "docId", "datasetName", "query", "roleCode")) {
            if (args.hasNonNull(key)) {
                return key + ":" + args.get(key).asText();
            }
        }
        return "—";
    }

    private String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    public Map<String, Object> budget(String sessionId) {
        BudgetService.BudgetState state = budgetService.state(sessionId);
        return Map.of("toolCalls", state.toolCalls(), "maxToolCalls", state.maxToolCalls(),
                "tokens", state.tokens(), "maxTokens", state.maxTokens(),
                "wallClockMs", state.wallClockMs(), "maxWallClockMs", state.maxWallClockMs(),
                "consecutiveFailures", state.consecutiveFailures(),
                "exhausted", state.exhausted());
    }

    public Instant now() {
        return Instant.now();
    }
}
