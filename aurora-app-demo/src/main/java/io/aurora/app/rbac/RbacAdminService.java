package io.aurora.app.rbac;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.DataScope;
import io.aurora.rbac.PermissionService;
import io.aurora.rbac.domain.SysDataScopeRule;
import io.aurora.rbac.domain.SysResource;
import io.aurora.rbac.domain.SysRole;
import io.aurora.rbac.domain.SysRoleResource;
import io.aurora.rbac.repo.SysDataScopeRuleRepository;
import io.aurora.rbac.repo.SysResourceRepository;
import io.aurora.rbac.repo.SysRoleRepository;
import io.aurora.rbac.repo.SysRoleResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The governance surface behind the Permission Center. Read access lives in
 * rbac-core; mutations live here so they can be audited by the app layer.
 */
@Service
public class RbacAdminService {

    private static final Set<String> BUILTIN_ROLES =
            Set.of("admin", "ops", "finance", "guest", "security");

    private final SysRoleRepository roleRepository;
    private final SysResourceRepository resourceRepository;
    private final SysRoleResourceRepository roleResourceRepository;
    private final SysDataScopeRuleRepository scopeRuleRepository;
    private final PermissionService permissionService;

    public RbacAdminService(SysRoleRepository roleRepository,
                            SysResourceRepository resourceRepository,
                            SysRoleResourceRepository roleResourceRepository,
                            SysDataScopeRuleRepository scopeRuleRepository,
                            PermissionService permissionService) {
        this.roleRepository = roleRepository;
        this.resourceRepository = resourceRepository;
        this.roleResourceRepository = roleResourceRepository;
        this.scopeRuleRepository = scopeRuleRepository;
        this.permissionService = permissionService;
    }

    public List<RoleAdminView> roles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(r -> r.code))
                .map(this::roleView)
                .toList();
    }

    public List<ResourceAdminView> resources() {
        return resourceRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::resourceView)
                .toList();
    }

    @Transactional
    public RoleAdminView createRole(String code, String name, String description, String scopeType) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]{1,31}")) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST,
                    "角色 code 必须为 2-32 位小写字母、数字或下划线");
        }
        if (roleRepository.findByCode(normalized).isPresent()) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "角色已存在：" + normalized);
        }
        SysRole role = new SysRole();
        role.code = normalized;
        role.name = name == null || name.isBlank() ? normalized : name.trim();
        role.description = description == null ? "" : description;
        roleRepository.save(role);
        replaceScope(role.id, scopeType);
        permissionService.invalidate();
        return roleView(role);
    }

    @Transactional
    public RoleAdminView updateRole(long roleId, String name, String description, String scopeType) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "角色不存在"));
        if (name != null && !name.isBlank()) role.name = name.trim();
        if (description != null) role.description = description;
        roleRepository.save(role);
        if (scopeType != null && !scopeType.isBlank()) {
            replaceScope(roleId, scopeType);
        }
        permissionService.invalidate();
        return roleView(role);
    }

    @Transactional
    public RoleAdminView setPermissions(long roleId, Set<String> codes) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "角色不存在"));
        roleResourceRepository.deleteAllByRoleId(roleId);
        if (codes != null) {
            for (String code : codes) {
                SysResource resource = resourceRepository.findByCode(code)
                        .orElseThrow(() -> new AuroraException(AuroraErrorCode.BAD_REQUEST,
                                "资源码不存在：" + code));
                roleResourceRepository.save(new SysRoleResource(roleId, resource.id));
            }
        }
        permissionService.invalidate();
        return roleView(role);
    }

    @Transactional
    public RoleAdminView togglePermission(long roleId, String code, boolean enabled) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "角色不存在"));
        SysResource resource = resourceRepository.findByCode(code)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND,
                        "资源码不存在：" + code));
        if (enabled) {
            boolean exists = roleResourceRepository.findByRoleId(roleId).stream()
                    .anyMatch(link -> link.resourceId.equals(resource.id));
            if (!exists) {
                roleResourceRepository.save(new SysRoleResource(roleId, resource.id));
            }
        } else {
            roleResourceRepository.findByRoleId(roleId).stream()
                    .filter(link -> link.resourceId.equals(resource.id))
                    .forEach(roleResourceRepository::delete);
        }
        permissionService.invalidate();
        return roleView(role);
    }

    @Transactional
    public ResourceAdminView createMenuGroup(String name, String parentCode, int sortOrder,
                                             String icon, String description) {
        if (name == null || name.isBlank()) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "菜单名称不能为空");
        }
        if (parentCode != null && !parentCode.isBlank()) {
            SysResource parent = resourceRepository.findByCode(parentCode)
                    .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND,
                            "父菜单不存在：" + parentCode));
            if (!"MENU".equals(parent.type)) {
                throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "父级必须是 MENU 资源");
            }
        }
        SysResource resource = new SysResource();
        resource.code = "menu:custom-" + UUID.randomUUID().toString().substring(0, 8);
        resource.name = name.trim();
        resource.type = "MENU";
        resource.parentCode = parentCode == null || parentCode.isBlank() ? null : parentCode;
        resource.sortOrder = sortOrder;
        resource.icon = icon == null || icon.isBlank() ? "folder" : icon;
        resource.description = description == null ? "" : description;
        resourceRepository.save(resource);
        roleRepository.findByCode("admin").ifPresent(admin ->
                roleResourceRepository.save(new SysRoleResource(admin.id, resource.id)));
        permissionService.invalidate();
        return resourceView(resource);
    }

    @Transactional
    public ResourceAdminView updateMenu(String code, String name, String parentCode,
                                        Integer sortOrder, String icon, String description) {
        SysResource resource = resourceRepository.findByCode(code)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "资源不存在"));
        if (!"MENU".equals(resource.type)) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "只有 MENU 资源可以在此编辑");
        }
        if (parentCode != null && !parentCode.isBlank()) {
            if (parentCode.equals(code)) {
                throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "菜单不能以自身为父级");
            }
            SysResource parent = resourceRepository.findByCode(parentCode)
                    .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND,
                            "父菜单不存在：" + parentCode));
            if (!"MENU".equals(parent.type)) {
                throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "父级必须是 MENU 资源");
            }
            resource.parentCode = parentCode;
        } else if (parentCode != null) {
            resource.parentCode = null;
        }
        if (name != null && !name.isBlank()) resource.name = name.trim();
        if (sortOrder != null) resource.sortOrder = sortOrder;
        if (icon != null && !icon.isBlank()) resource.icon = icon;
        if (description != null) resource.description = description;
        resourceRepository.save(resource);
        return resourceView(resource);
    }

    @Transactional
    public void deleteMenuGroup(String code) {
        SysResource resource = resourceRepository.findByCode(code)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "资源不存在"));
        if (!"MENU".equals(resource.type) || !code.startsWith("menu:custom-")) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "只能删除自定义菜单组");
        }
        boolean hasChildren = resourceRepository.findAll().stream()
                .anyMatch(r -> code.equals(r.parentCode));
        if (hasChildren) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "请先移动或删除该菜单组下的子项");
        }
        roleResourceRepository.findAll().stream()
                .filter(link -> link.resourceId.equals(resource.id))
                .forEach(roleResourceRepository::delete);
        resourceRepository.delete(resource);
    }

    public List<MenuNode> menuTree() {
        List<SysResource> menus = resourceRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(r -> "MENU".equals(r.type))
                .toList();
        Map<String, List<SysResource>> children = new LinkedHashMap<>();
        for (SysResource resource : menus) {
            String parent = resource.parentCode == null ? "" : resource.parentCode;
            children.computeIfAbsent(parent, key -> new java.util.ArrayList<>()).add(resource);
        }
        return children.getOrDefault("", List.of()).stream()
                .sorted(Comparator.comparingInt(r -> r.sortOrder))
                .map(root -> node(root, children))
                .toList();
    }

    private MenuNode node(SysResource resource, Map<String, List<SysResource>> children) {
        List<MenuNode> descendants = children.getOrDefault(resource.code, List.of()).stream()
                .sorted(Comparator.comparingInt(r -> r.sortOrder))
                .map(child -> node(child, children))
                .toList();
        return new MenuNode(resource.code, resource.name, resource.icon, resource.sortOrder,
                resource.parentCode, descendants);
    }

    private RoleAdminView roleView(SysRole role) {
        String scope = scopeRuleRepository.findByRoleId(role.id).stream()
                .findFirst().map(rule -> rule.type).orElse(DataScope.Type.SELF.name());
        return new RoleAdminView(role.id, role.code, role.name, role.description, scope,
                permissionService.permissionsForRole(role.id).stream().sorted().toList(),
                BUILTIN_ROLES.contains(role.code));
    }

    private ResourceAdminView resourceView(SysResource resource) {
        return new ResourceAdminView(resource.id, resource.code, resource.name, resource.type,
                resource.parentCode, resource.sortOrder, resource.icon, resource.description);
    }

    private void replaceScope(long roleId, String scopeType) {
        String type = scopeType == null || scopeType.isBlank()
                ? DataScope.Type.SELF.name() : scopeType.toUpperCase(Locale.ROOT);
        try {
            DataScope.Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "未知数据范围：" + scopeType);
        }
        scopeRuleRepository.deleteAllByRoleId(roleId);
        SysDataScopeRule rule = new SysDataScopeRule();
        rule.roleId = roleId;
        rule.type = type;
        rule.customDeptIds = "";
        scopeRuleRepository.save(rule);
    }

    public record RoleAdminView(long id, String code, String name, String description,
                                String scopeType, List<String> permissions, boolean builtin) {
    }

    public record ResourceAdminView(long id, String code, String name, String type,
                                    String parentCode, int sortOrder, String icon,
                                    String description) {
    }

    public record MenuNode(String code, String name, String icon, int sortOrder,
                           String parentCode, List<MenuNode> children) {
    }
}
