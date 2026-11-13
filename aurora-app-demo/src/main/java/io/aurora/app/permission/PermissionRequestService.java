package io.aurora.app.permission;

import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.Actor;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import io.aurora.rbac.UserQueryService;
import io.aurora.rbac.domain.SysRole;
import io.aurora.rbac.repo.SysRoleRepository;
import io.aurora.rbac.repo.SysUserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Permission requests are a governance workflow, not a Tool execution:
 * users ask for a role, an independent approver grants it, every transition is
 * audited. Admin/security roles are not requestable.
 */
@Service
public class PermissionRequestService {

    private static final Set<String> NON_REQUESTABLE = Set.of("admin", "security");

    private final PermissionRequestRepository repository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final UserQueryService userQueryService;
    private final AuditService auditService;

    public PermissionRequestService(PermissionRequestRepository repository,
                                    SysRoleRepository roleRepository,
                                    SysUserRoleRepository userRoleRepository,
                                    UserQueryService userQueryService,
                                    AuditService auditService) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userQueryService = userQueryService;
        this.auditService = auditService;
    }

    public List<RoleOption> requestableRoles(Principal principal) {
        return roleRepository.findAll().stream()
                .filter(role -> !NON_REQUESTABLE.contains(role.code))
                .map(role -> new RoleOption(role.code, role.name, role.description,
                        userRoleRepository.existsByUserIdAndRoleId(principal.userId(), role.id)))
                .toList();
    }

    @Transactional
    public PermissionRequestView create(Principal principal, String roleCode, String reason) {
        SysRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND,
                        "角色不存在：" + roleCode));
        if (NON_REQUESTABLE.contains(role.code)) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                    "管理员与安全审计员角色不能通过申请获得");
        }
        if (userRoleRepository.existsByUserIdAndRoleId(principal.userId(), role.id)) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "你已经拥有该角色");
        }
        if (repository.existsByRequesterUserIdAndRoleCodeAndStatus(principal.userId(),
                role.code, "PENDING")) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "该角色的申请已在审批中");
        }
        PermissionRequestEntity entity = new PermissionRequestEntity();
        entity.id = UUID.randomUUID().toString();
        entity.requesterUserId = principal.userId();
        entity.requesterUsername = principal.username();
        entity.requesterName = principal.displayName();
        entity.roleId = role.id;
        entity.roleCode = role.code;
        entity.roleName = role.name;
        entity.reason = reason == null ? "" : reason.trim();
        entity.status = "PENDING";
        entity.createdAt = Instant.now();
        repository.save(entity);
        audit(principal, "PERMISSION_REQUESTED", entity.id, "PENDING");
        return view(entity);
    }

    public List<PermissionRequestView> list(Principal principal) {
        List<PermissionRequestEntity> rows = principal.can("approval:decide")
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByRequesterUserIdOrderByCreatedAtDesc(principal.userId());
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public DecisionOutcome decide(String id, Principal approver, boolean approve, String note) {
        if (!approver.can("approval:decide")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                    "缺少 approval:decide 权限");
        }
        PermissionRequestEntity entity = repository.findById(id)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "申请不存在"));
        if (!"PENDING".equals(entity.status)) {
            throw new AuroraException(AuroraErrorCode.CONFLICT,
                    "申请状态为 " + entity.status + "，不能再次处理");
        }
        if (entity.requesterUserId == approver.userId()) {
            throw new AuroraException(AuroraErrorCode.SELF_APPROVAL_FORBIDDEN,
                    "不能审批自己的权限申请");
        }
        String target = approve ? "APPROVED" : "REJECTED";
        int changed = repository.claimDecision(id, "PENDING", target,
                approver.displayName(), Instant.now(), note);
        if (changed != 1) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "申请已被其他审批人处理");
        }
        if (approve) {
            try {
                userQueryService.assignRole(approver, entity.requesterUsername, entity.roleCode);
                audit(approver, "PERMISSION_GRANTED", entity.id, "OK");
            } catch (AuroraException e) {
                repository.markFailed(id, "APPROVED", "FAILED", approver.displayName(),
                        Instant.now(), e.getMessage());
                audit(approver, "PERMISSION_GRANT_FAILED", entity.id, "ERROR");
                throw e;
            }
        } else {
            audit(approver, "PERMISSION_REJECTED", entity.id, "OK");
        }
        return new DecisionOutcome(view(repository.findById(id).orElseThrow()));
    }

    public record RoleOption(String code, String name, String description, boolean alreadyAssigned) {
    }

    public record PermissionRequestView(String id, long requesterUserId, String requesterName,
                                        String roleCode, String roleName, String reason,
                                        String status, String decidedBy, String decidedAt,
                                        String note, String createdAt) {
    }

    public record DecisionOutcome(PermissionRequestView request) {
    }

    private PermissionRequestView view(PermissionRequestEntity entity) {
        return new PermissionRequestView(entity.id, entity.requesterUserId, entity.requesterName,
                entity.roleCode, entity.roleName, entity.reason, entity.status, entity.decidedBy,
                entity.decidedAt == null ? null : entity.decidedAt.toString(), entity.note,
                entity.createdAt.toString());
    }

    private void audit(Principal principal, String decision, String requestId, String status) {
        auditService.record(AuditDraft.of(AuditService.TYPE_PERMISSION)
                .trace("perm-" + UUID.randomUUID()).session("permission-request")
                .actor(Actor.user()).onBehalfOf(principal)
                .decision(decision).status(status)
                .refs(List.of(Map.of("objectType", "permission_request", "objectId", requestId))));
    }
}
