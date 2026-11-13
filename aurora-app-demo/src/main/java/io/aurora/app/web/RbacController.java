package io.aurora.app.web;

import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.DataScopeResolver;
import io.aurora.rbac.PermissionService;
import io.aurora.rbac.Principal;
import io.aurora.rbac.RbacViews;
import io.aurora.rbac.domain.SysResource;
import io.aurora.rbac.domain.SysRole;
import io.aurora.rbac.repo.SysDataScopeRuleRepository;
import io.aurora.rbac.repo.SysRoleRepository;
import io.aurora.rbac.repo.SysUserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac")
public class RbacController {

    private final PermissionService permissionService;
    private final SysRoleRepository roleRepository;
    private final SysDataScopeRuleRepository scopeRuleRepository;

    public RbacController(PermissionService permissionService, SysRoleRepository roleRepository,
                          SysDataScopeRuleRepository scopeRuleRepository) {
        this.permissionService = permissionService;
        this.roleRepository = roleRepository;
        this.scopeRuleRepository = scopeRuleRepository;
    }

    @GetMapping("/matrix")
    public ApiResponse<MatrixView> matrix(@RequestAttribute Principal principal) {
        require(principal);
        List<SysRole> roles = roleRepository.findAll().stream()
                .sorted(Comparator.comparing(r -> r.code)).toList();
        Map<Long, String> roleCodes = new HashMap<>();
        Map<Long, List<String>> rolePermissions = new HashMap<>();
        for (SysRole role : roles) {
            roleCodes.put(role.id, role.code);
            rolePermissions.put(role.id, permissionService.permissionsForRole(role.id).stream().toList());
        }
        List<MatrixRowView> rows = new ArrayList<>();
        for (SysResource resource : permissionService.allResources()) {
            List<String> holders = roles.stream()
                    .filter(r -> rolePermissions.get(r.id).contains(resource.code))
                    .map(r -> r.code)
                    .toList();
            rows.add(new MatrixRowView(resource.code, resource.name, resource.type, holders));
        }
        List<RoleSummary> roleViews = roles.stream()
                .map(r -> new RoleSummary(r.id, r.code, r.name, r.description,
                        scopeRuleRepository.findByRoleId(r.id).stream()
                                .findFirst().map(rule -> rule.type).orElse("SELF")))
                .toList();
        return ApiResponse.ok(new MatrixView(roleViews, rows));
    }

    @GetMapping("/resources")
    public ApiResponse<List<ResourceView>> resources(@RequestAttribute Principal principal) {
        require(principal);
        return ApiResponse.ok(permissionService.allResources().stream()
                .map(r -> new ResourceView(r.code, r.name, r.type, r.parentCode, r.description))
                .toList());
    }

    private void require(Principal principal) {
        if (!principal.can("rbac:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 rbac:read 权限");
        }
    }

    public record RoleSummary(long id, String code, String name, String description, String scopeType) {
    }

    public record MatrixRowView(String code, String name, String type, List<String> roles) {
    }

    public record MatrixView(List<RoleSummary> roles, List<MatrixRowView> rows) {
    }

    public record ResourceView(String code, String name, String type, String parentCode,
                               String description) {
    }
}
