package io.aurora.agent.approval;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.gateway.GatewayOutcome;
import io.aurora.agent.gateway.ToolInvocationGateway;
import io.aurora.agent.trace.TraceService;
import io.aurora.contract.Actor;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.rbac.PrincipalLoader;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.ApprovalPresentation;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Approval state machine with CAS transitions, argument binding, separation
 * of duties and pre-execution re-validation (PRD §5.4, AC-05..AC-08).
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repository;
    private final ToolRegistry toolRegistry;
    private final PrincipalLoader principalLoader;
    private final AuditService auditService;
    private final TraceService traceService;
    private final ObjectProvider<ToolInvocationGateway> gatewayProvider;
    private final int ttlHours;
    private final boolean allowSelfApproveWrite;

    public ApprovalService(ApprovalRepository repository,
                           ToolRegistry toolRegistry,
                           PrincipalLoader principalLoader,
                           AuditService auditService,
                           TraceService traceService,
                           ObjectProvider<ToolInvocationGateway> gatewayProvider,
                           @Value("${aurora.approval.ttlHours:24}") int ttlHours,
                           @Value("${aurora.approval.allowSelfApproveWrite:false}") boolean allowSelfApproveWrite) {
        this.repository = repository;
        this.toolRegistry = toolRegistry;
        this.principalLoader = principalLoader;
        this.auditService = auditService;
        this.traceService = traceService;
        this.gatewayProvider = gatewayProvider;
        this.ttlHours = ttlHours;
        this.allowSelfApproveWrite = allowSelfApproveWrite;
    }

    @Transactional
    public ApprovalEntity createPending(ToolDefinition definition, Principal requester, Actor actor,
                                        JsonNode args, String argsSummary, String affectedObjects,
                                        int affectedCount, String sessionId, String traceId) {
        return createPending(definition, requester, actor, args, argsSummary, affectedObjects,
                affectedCount, sessionId, traceId, ApprovalPresentation.generic());
    }

    public ApprovalEntity createPending(ToolDefinition definition, Principal requester, Actor actor,
                                        JsonNode args, String argsSummary, String affectedObjects,
                                        int affectedCount, String sessionId, String traceId,
                                        ApprovalPresentation presentation) {
        ApprovalEntity entity = new ApprovalEntity();
        entity.id = UUID.randomUUID().toString();
        entity.toolName = definition.name();
        entity.toolVersion = definition.version();
        entity.argsJson = Json.canonical(args);
        entity.argsHash = Json.argsHash(args);
        entity.argsSummary = truncate(argsSummary, 500);
        entity.intentSummary = truncate(presentation.intent(), 500);
        entity.operability = presentation.operability();
        entity.riskLevel = presentation.riskLevel();
        entity.riskReasons = truncate(String.join("；", presentation.riskReasons()), 500);
        entity.requiresConfirmation = presentation.requiresConfirmation();
        entity.affectedObjects = truncate(affectedObjects, 500);
        entity.affectedCount = affectedCount;
        entity.sideEffect = definition.sideEffect().name();
        entity.reversible = reversibleLabel(definition);
        entity.requesterUserId = requester.userId();
        entity.requesterName = requester.displayName();
        entity.requesterRoleId = requester.roleIds().stream().findFirst().orElse(0L);
        entity.actorType = actor.type().name();
        entity.agentId = actor.agentId();
        entity.sessionId = sessionId;
        entity.traceId = traceId;
        entity.status = ApprovalStatus.PENDING;
        entity.createdAt = Instant.now();
        entity.expiresAt = entity.createdAt.plus(ttlHours, ChronoUnit.HOURS);
        return repository.save(entity);
    }

    public List<ApprovalView> list(Principal principal, String status) {
        requireRead(principal);
        List<ApprovalEntity> entities = status == null || status.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        return entities.stream().map(ApprovalView::from).toList();
    }

    public long pendingCount() {
        return repository.countByStatus(ApprovalStatus.PENDING);
    }

    public ApprovalView get(String id) {
        return ApprovalView.from(repository.findById(id)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "审批单不存在")));
    }

    /**
     * Approve or reject one approval. Returns the new view plus the gateway
     * outcome when an approved tool actually ran.
     */
    public DecisionOutcome decide(String id, Principal approver, Actor approverActor,
                                  boolean approve, String note) {
        return decide(id, approver, approverActor, approve, note, false);
    }

    public DecisionOutcome decide(String id, Principal approver, Actor approverActor,
                                  boolean approve, String note, boolean confirmRisk) {
        ApprovalEntity entity = repository.findById(id)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "审批单不存在"));
        if (entity.status != ApprovalStatus.PENDING) {
            throw new AuroraException(AuroraErrorCode.CONFLICT,
                    "审批单状态为 " + entity.status + "，不能再次处理");
        }
        if (entity.expiresAt.isBefore(Instant.now())) {
            repository.claimExpiry(id, ApprovalStatus.PENDING, ApprovalStatus.EXPIRED, Instant.now());
            auditDecision(entity, approver, "EXPIRED", "OK", "system", "审批已过期");
            return new DecisionOutcome(get(id), null);
        }
        if (!approver.can("approval:decide")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 approval:decide 权限");
        }
        ToolDefinition definition = toolRegistry.find(entity.toolName)
                .map(ToolRegistry.RegisteredTool::definition)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.TOOL_NOT_REGISTERED,
                        "审批关联的 Tool 已从注册表移除"));
        for (String permission : definition.requiredPermissions()) {
            if (!approver.can(permission)) {
                throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                        "审批人缺少该 Tool 的权限：" + permission);
            }
        }
        if (approver.userId() == entity.requesterUserId && isSelfApprovalForbidden(definition)) {
            String effect = switch (definition.sideEffect()) {
                case READ -> "只读";
                case WRITE -> "写入";
                case DESTRUCTIVE -> "破坏性";
            };
            throw new AuroraException(AuroraErrorCode.SELF_APPROVAL_FORBIDDEN,
                    "职责分离：不能批准自己发起的" + effect + "操作");
        }
        if (approve && entity.requiresConfirmation && !confirmRisk) {
            throw new AuroraException(AuroraErrorCode.RISK_CONFIRMATION_REQUIRED,
                    "该操作可操作度较低，需要二次确认风险后才能批准");
        }

        if (!approve) {
            int changed = repository.claimDecision(id, ApprovalStatus.PENDING, ApprovalStatus.REJECTED,
                    approver.displayName(), Instant.now(), truncate(note, 500));
            if (changed != 1) {
                throw new AuroraException(AuroraErrorCode.CONFLICT, "审批单已被其他审批人处理");
            }
            auditDecision(entity, approver, "REJECTED", "OK", "USER", "审批被拒绝，业务数据未变更");
            return new DecisionOutcome(get(id), null);
        }

        int changed = repository.claimDecision(id, ApprovalStatus.PENDING, ApprovalStatus.APPROVED,
                approver.displayName(), Instant.now(), truncate(note, 500));
        if (changed != 1) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "审批单已被其他审批人处理");
        }
        auditDecision(entity, approver, "APPROVED", "OK", "USER", "审批通过，开始执行");
        traceService.append(entity.traceId, "approval.approved", "ok", "APPROVED",
                Map.of("approvalId", id, "approver", approver.displayName()));
        GatewayOutcome outcome = executeApproved(id);
        return new DecisionOutcome(get(id), outcome);
    }

    /**
     * Re-validates and executes an APPROVED approval. Exactly one caller wins
     * the CAS PENDING→APPROVED transition, so this runs once (AC-08).
     */
    public GatewayOutcome executeApproved(String id) {
        ApprovalEntity entity = repository.findById(id)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "审批单不存在"));
        if (entity.status != ApprovalStatus.APPROVED) {
            throw new AuroraException(AuroraErrorCode.CONFLICT,
                    "审批单状态为 " + entity.status + "，不可执行");
        }
        ToolDefinition definition = toolRegistry.find(entity.toolName)
                .map(ToolRegistry.RegisteredTool::definition)
                .orElse(null);
        Principal requester;
        try {
            if (definition == null) {
                throw new AuroraException(AuroraErrorCode.TOOL_NOT_REGISTERED, "Tool 已从注册表移除");
            }
            if (!toolRegistry.isActive(entity.toolName)) {
                throw new AuroraException(AuroraErrorCode.TOOL_NOT_ACTIVE, "Tool 已停用");
            }
            requester = principalLoader.load(entity.requesterUserId, entity.requesterRoleId);
            for (String permission : definition.requiredPermissions()) {
                if (!requester.can(permission)) {
                    throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                            "发起人已被撤销权限：" + permission);
                }
            }
            JsonNode args = Json.parse(entity.argsJson);
            if (!Json.argsHash(args).equals(entity.argsHash)) {
                throw new AuroraException(AuroraErrorCode.ARGS_MISMATCH, "审批参数哈希不一致，拒绝执行");
            }
            if (entity.expiresAt.isBefore(Instant.now())) {
                throw new AuroraException(AuroraErrorCode.APPROVAL_EXPIRED, "审批已过期");
            }
        } catch (AuroraException e) {
            fail(entity, e.code().name(), e.getMessage());
            return GatewayOutcome.failed(e.code().name(), e.getMessage(), entity.traceId,
                    entity.sessionId, null, List.of(
                            new GatewayOutcome.PipelineStep("审批执行前复验", "bad", e.getMessage())));
        }

        Actor actor = new Actor(Actor.Type.valueOf(entity.actorType), entity.agentId);
        GatewayOutcome outcome = gatewayProvider.getObject()
                .executeApproved(definition, requester, actor, Json.parse(entity.argsJson),
                        entity.id, entity.traceId, entity.sessionId);

        if (outcome.status() == GatewayOutcome.Status.EXECUTED) {
            int changed = repository.markExecution(id, ApprovalStatus.APPROVED, ApprovalStatus.EXECUTED,
                    Instant.now(), outcome.data() == null ? "{}" : Json.write(outcome.data()));
            if (changed == 1) {
                auditDecision(entity, requester, "EXECUTED", "OK", "AGENT",
                        "审批执行完成：" + (outcome.summary() == null ? "" : outcome.summary()));
            }
        } else {
            fail(entity, outcome.errorCode(), outcome.message());
        }
        return outcome;
    }

    public List<DecisionOutcome> decideBatch(List<String> ids, Principal approver, Actor actor,
                                             boolean approve, String note) {
        return decideBatch(ids, approver, actor, approve, note, false);
    }

    public List<DecisionOutcome> decideBatch(List<String> ids, Principal approver, Actor actor,
                                             boolean approve, String note, boolean confirmRisk) {
        List<DecisionOutcome> results = new ArrayList<>();
        for (String id : ids) {
            try {
                results.add(decide(id, approver, actor, approve, note, confirmRisk));
            } catch (AuroraException e) {
                results.add(new DecisionOutcome(get(id), null, e.code().name(), e.getMessage()));
            }
        }
        return results;
    }

    private void fail(ApprovalEntity entity, String code, String message) {
        repository.markFailure(entity.id, ApprovalStatus.APPROVED, ApprovalStatus.FAILED,
                Instant.now(), code);
        auditService.record(AuditDraft.of(AuditService.TYPE_APPROVAL)
                .trace(entity.traceId).session(entity.sessionId)
                .actor(new Actor(Actor.Type.valueOf(entity.actorType), entity.agentId))
                .identity(entity.requesterUserId, entity.requesterName, List.of())
                .tool(entity.toolName).approval(entity.id)
                .decision("FAILED").status("ERROR"));
        traceService.append(entity.traceId, "approval.failed", "bad", code,
                Map.of("approvalId", entity.id, "error", message == null ? "" : message));
    }

    private void auditDecision(ApprovalEntity entity, Principal actorPrincipal, String decision,
                               String status, String actorType, String detail) {
        auditService.record(AuditDraft.of(AuditService.TYPE_APPROVAL)
                .trace(entity.traceId).session(entity.sessionId)
                .actor(new Actor(Actor.Type.valueOf(actorType), entity.agentId))
                .onBehalfOf(actorPrincipal)
                .tool(entity.toolName).approval(entity.id)
                .decision(decision).status(status)
                .refs(List.of(Map.of("objectType", "approval", "objectId", entity.id,
                        "detail", detail))));
    }

    private boolean isSelfApprovalForbidden(ToolDefinition definition) {
        if (definition.sideEffect() == SideEffect.DESTRUCTIVE) {
            return true;
        }
        return definition.sideEffect() == SideEffect.WRITE && !allowSelfApproveWrite;
    }

    private void requireRead(Principal principal) {
        if (!principal.can("approval:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 approval:read 权限");
        }
    }

    private ApprovalStatus parseStatus(String status) {
        try {
            return ApprovalStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "未知审批状态：" + status);
        }
    }

    private String reversibleLabel(ToolDefinition definition) {
        if (definition.compensation() == null || definition.compensation().isBlank()
                || "NONE".equalsIgnoreCase(definition.compensation())) {
            return "不可回滚";
        }
        return "可回滚（" + definition.compensation() + "）";
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public record DecisionOutcome(ApprovalView approval, GatewayOutcome execution,
                                  String errorCode, String error) {
        public DecisionOutcome(ApprovalView approval, GatewayOutcome execution) {
            this(approval, execution, null, null);
        }

        public boolean success() {
            return errorCode == null;
        }
    }
}
