package io.aurora.app.web;

import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.app.rbac.RbacAdminService;
import io.aurora.contract.Actor;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/rbac/admin")
public class RbacAdminController {

    private final RbacAdminService admin;
    private final AuditService auditService;

    public RbacAdminController(RbacAdminService admin, AuditService auditService) {
        this.admin = admin;
        this.auditService = auditService;
    }

    @GetMapping("/roles")
    public ApiResponse<List<RbacAdminService.RoleAdminView>> roles(@RequestAttribute Principal principal) {
        requireRead(principal);
        return ApiResponse.ok(admin.roles());
    }

    @PostMapping("/roles")
    public ApiResponse<RbacAdminService.RoleAdminView> createRole(
            @RequestAttribute Principal principal, @RequestBody RoleRequest request) {
        requireManage(principal);
        audit(principal, "CREATE_ROLE", "role", request.code());
        return ApiResponse.ok(admin.createRole(request.code(), request.name(),
                request.description(), request.scopeType()));
    }

    @PatchMapping("/roles/{id}")
    public ApiResponse<RbacAdminService.RoleAdminView> updateRole(
            @RequestAttribute Principal principal, @PathVariable long id,
            @RequestBody RoleUpdateRequest request) {
        requireManage(principal);
        audit(principal, "UPDATE_ROLE", "role", String.valueOf(id));
        return ApiResponse.ok(admin.updateRole(id, request.name(), request.description(),
                request.scopeType()));
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<RbacAdminService.RoleAdminView> setPermissions(
            @RequestAttribute Principal principal, @PathVariable long id,
            @RequestBody PermissionSetRequest request) {
        requireManage(principal);
        audit(principal, "SET_ROLE_PERMISSIONS", "role", String.valueOf(id));
        return ApiResponse.ok(admin.setPermissions(id, Set.copyOf(request.permissions())));
    }

    @PostMapping("/roles/{id}/permissions/{code}/toggle")
    public ApiResponse<RbacAdminService.RoleAdminView> toggle(
            @RequestAttribute Principal principal, @PathVariable long id,
            @PathVariable String code, @RequestParam boolean enabled) {
        requireManage(principal);
        audit(principal, enabled ? "GRANT_PERMISSION" : "REVOKE_PERMISSION", "role", String.valueOf(id));
        return ApiResponse.ok(admin.togglePermission(id, code, enabled));
    }

    @GetMapping("/resources")
    public ApiResponse<List<RbacAdminService.ResourceAdminView>> resources(
            @RequestAttribute Principal principal) {
        requireRead(principal);
        return ApiResponse.ok(admin.resources());
    }

    @GetMapping("/menu")
    public ApiResponse<List<RbacAdminService.MenuNode>> menu(@RequestAttribute Principal principal) {
        requireRead(principal);
        return ApiResponse.ok(admin.menuTree());
    }

    @PostMapping("/menu")
    public ApiResponse<RbacAdminService.ResourceAdminView> createMenu(
            @RequestAttribute Principal principal, @RequestBody MenuRequest request) {
        requireManage(principal);
        audit(principal, "CREATE_MENU", "menu", request.name());
        return ApiResponse.ok(admin.createMenuGroup(request.name(), request.parentCode(),
                request.sortOrder(), request.icon(), request.description()));
    }

    @PatchMapping("/menu/{code}")
    public ApiResponse<RbacAdminService.ResourceAdminView> updateMenu(
            @RequestAttribute Principal principal, @PathVariable String code,
            @RequestBody MenuRequest request) {
        requireManage(principal);
        audit(principal, "UPDATE_MENU", "menu", code);
        return ApiResponse.ok(admin.updateMenu(code, request.name(), request.parentCode(),
                request.sortOrder(), request.icon(), request.description()));
    }

    @DeleteMapping("/menu/{code}")
    public ApiResponse<Boolean> deleteMenu(@RequestAttribute Principal principal,
                                           @PathVariable String code) {
        requireManage(principal);
        audit(principal, "DELETE_MENU", "menu", code);
        admin.deleteMenuGroup(code);
        return ApiResponse.ok(true);
    }

    private void requireRead(Principal principal) {
        if (!principal.can("rbac:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 rbac:read 权限");
        }
    }

    private void requireManage(Principal principal) {
        if (!principal.can("rbac:manage")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                    "缺少 rbac:manage 权限，只有超级管理员可以修改权限中心");
        }
    }

    private void audit(Principal principal, String decision, String objectType, String objectId) {
        auditService.record(AuditDraft.of(AuditService.TYPE_PERMISSION)
                .trace("rbac-" + UUID.randomUUID()).session("rbac")
                .actor(Actor.user()).onBehalfOf(principal)
                .decision(decision).status("OK")
                .refs(List.of(Map.of("objectType", objectType,
                        "objectId", objectId == null ? "" : objectId))));
    }

    public record RoleRequest(String code, String name, String description, String scopeType) {
    }

    public record RoleUpdateRequest(String name, String description, String scopeType) {
    }

    public record PermissionSetRequest(List<String> permissions) {
    }

    public record MenuRequest(String name, String parentCode, int sortOrder, String icon,
                              String description) {
    }
}
