package io.aurora.rbac;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.domain.SysDepartment;
import io.aurora.rbac.domain.SysRole;
import io.aurora.rbac.domain.SysUser;
import io.aurora.rbac.domain.SysUserRole;
import io.aurora.rbac.repo.SysDepartmentRepository;
import io.aurora.rbac.repo.SysRoleRepository;
import io.aurora.rbac.repo.SysUserRepository;
import io.aurora.rbac.repo.SysUserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The framework data access layer for people. DataScope is applied here,
 * never hand-written inside a Tool.
 */
@Service
public class UserQueryService {

    private final SysUserRepository userRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleRepository roleRepository;
    private final SysDepartmentRepository departmentRepository;
    private final PermissionService permissionService;
    private final PasswordHasher passwordHasher;

    public UserQueryService(SysUserRepository userRepository,
                            SysUserRoleRepository userRoleRepository,
                            SysRoleRepository roleRepository,
                            SysDepartmentRepository departmentRepository,
                            PermissionService permissionService,
                            PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.permissionService = permissionService;
        this.passwordHasher = passwordHasher;
    }

    public List<RbacViews.UserView> search(Principal principal, String query, int limit) {
        return search(principal, query, limit, false);
    }

    public List<RbacViews.UserView> search(Principal principal, String query, int limit,
                                           boolean includeDisabled) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<SysUser> candidates = scopedCandidates(principal);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<Long, String> deptNames = departmentNames();
        Map<Long, List<String>> rolesByUser = rolesByUser();

        return candidates.stream()
                .filter(u -> includeDisabled || "ACTIVE".equals(u.status))
                .filter(u -> needle.isEmpty()
                        || u.displayName.toLowerCase(Locale.ROOT).contains(needle)
                        || u.username.toLowerCase(Locale.ROOT).contains(needle)
                        || u.title.toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(u -> u.displayName))
                .limit(safeLimit)
                .map(u -> toView(u, deptNames, rolesByUser))
                .toList();
    }

    public UserPage searchPage(Principal principal, String query, int page, int size,
                               boolean includeDisabled) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        DataScope scope = principal.dataScope();
        boolean allScope = scope.isAll();
        Collection<Long> deptIds = scope.deptIds().isEmpty()
                ? List.of(-1L) : new ArrayList<>(scope.deptIds());
        var pageable = org.springframework.data.domain.PageRequest.of(safePage, safeSize,
                org.springframework.data.domain.Sort.by("displayName"));
        var result = userRepository.searchScoped(allScope, principal.userId(), deptIds,
                includeDisabled, query == null ? "" : query.trim(), pageable);
        Map<Long, String> deptNames = departmentNames();
        Map<Long, List<String>> rolesByUser = rolesByUser();
        List<RbacViews.UserView> items = result.getContent().stream()
                .map(u -> toView(u, deptNames, rolesByUser))
                .toList();
        return new UserPage(items, result.getTotalElements(), safePage, safeSize,
                result.getTotalPages());
    }

    public RbacViews.UserView findByUsername(Principal principal, String identifier) {
        SysUser user = resolveUser(identifier);
        requireInScope(principal, user);
        return toView(user, departmentNames(), rolesByUser());
    }

    /**
     * Accepts the stable username or the human display name. Ambiguous display
     * names are rejected rather than guessing.
     */
    private SysUser resolveUser(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> {
                    List<SysUser> matches = userRepository.findAll().stream()
                            .filter(u -> u.displayName.equals(identifier))
                            .toList();
                    if (matches.size() > 1) {
                        throw new AuroraException(AuroraErrorCode.NOT_FOUND,
                                "匹配到多个用户，请使用 username：" + identifier);
                    }
                    return matches.stream().findFirst();
                })
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND,
                        "用户不存在：" + identifier));
    }

    @Transactional
    public RbacViews.UserView createUser(Principal principal, String username, String displayName,
                               String title, Long deptId, String password, String roleCode) {
        if (username == null || !username.matches("[a-z0-9_.-]{3,32}")) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST,
                    "username 必须是 3-32 位小写字母、数字、点、下划线或短横线");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "用户名已存在：" + username);
        }
        if (displayName == null || displayName.isBlank()) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "显示名称不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "密码至少 6 位");
        }
        long resolvedDept = deptId == null ? defaultDept(principal) : deptId;
        requireDeptInScope(principal, resolvedDept);
        SysRole role = roleCode == null || roleCode.isBlank() ? null
                : roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND,
                        "角色不存在：" + roleCode));
        if (role != null) {
            assertCanGrantRole(principal, role);
        }
        SysUser user = new SysUser(username, displayName,
                passwordHasher.hash(password), resolvedDept);
        user.title = title == null || title.isBlank() ? "成员" : title;
        user.avatarHue = Math.floorMod(username.hashCode(), 360);
        user.status = "ACTIVE";
        userRepository.save(user);
        if (role != null) {
            userRoleRepository.save(new SysUserRole(user.id, role.id));
        }
        return toView(user, departmentNames(), rolesByUser());
    }

    @Transactional
    public RbacViews.UserView updateOwnProfile(Principal principal, String displayName, String title) {
        SysUser user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "用户不存在"));
        if (displayName != null && !displayName.isBlank()) {
            user.displayName = displayName.trim();
        }
        if (title != null && !title.isBlank()) {
            user.title = title.trim();
        }
        userRepository.save(user);
        return toView(user, departmentNames(), rolesByUser());
    }

    @Transactional
    public RbacViews.UserView updateUser(Principal principal, String username, String displayName,
                               String title, Long deptId, String status) {
        SysUser target = resolveUser(username);
        requireInScope(principal, target);
        if (deptId != null && deptId != target.deptId) {
            requireDeptInScope(principal, deptId);
            target.deptId = deptId;
        }
        if (displayName != null && !displayName.isBlank()) {
            target.displayName = displayName;
        }
        if (title != null && !title.isBlank()) {
            target.title = title;
        }
        if (status != null) {
            if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
                throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "status 只能是 ACTIVE 或 DISABLED");
            }
            if (target.id == principal.userId() && "DISABLED".equals(status)) {
                throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "不能停用当前登录用户");
            }
            target.status = status;
        }
        userRepository.save(target);
        return toView(target, departmentNames(), rolesByUser());
    }

    @Transactional
    public AssignResult assignRole(Principal principal, String username, String roleCode) {
        SysUser target = resolveUser(username);
        requireInScope(principal, target);
        SysRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "角色不存在：" + roleCode));
        assertCanGrantRole(principal, role);
        boolean alreadyAssigned = userRoleRepository.existsByUserIdAndRoleId(target.id, role.id);
        if (!alreadyAssigned) {
            userRoleRepository.save(new SysUserRole(target.id, role.id));
        }
        return new AssignResult(target.id, target.username, target.displayName, role.code, role.name,
                !alreadyAssigned);
    }

    public UserMeta meta(Principal principal) {
        Set<Long> allowed = allowedDeptIds(principal);
        List<DepartmentOption> departments = departmentRepository.findAll().stream()
                .filter(d -> allowed == null || allowed.contains(d.id))
                .sorted(Comparator.comparing(d -> d.sortOrder))
                .map(d -> new DepartmentOption(d.id, d.name, d.parentId))
                .toList();
        List<RoleOption> roles = roleRepository.findAll().stream()
                .sorted(Comparator.comparing(r -> r.code))
                .map(r -> new RoleOption(r.code, r.name, r.description,
                        canGrant(principal, r)))
                .toList();
        return new UserMeta(departments, roles, principal.dataScope().type().name());
    }

    @Transactional
    public DeleteResult deleteUser(Principal principal, String username) {
        SysUser target = resolveUser(username);
        if (target.id == principal.userId()) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "不能删除当前登录用户");
        }
        requireInScope(principal, target);
        int rolesRemoved = userRoleRepository.deleteAllByUserId(target.id);
        userRepository.delete(target);
        return new DeleteResult(target.id, target.username, target.displayName, rolesRemoved);
    }

    public List<RbacViews.RoleView> listRoles(Principal principal) {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(r -> r.code))
                .map(r -> new RbacViews.RoleView(r.id, r.code, r.name, r.description, "—"))
                .toList();
    }

    private boolean canGrant(Principal principal, SysRole role) {
        return permissionService.permissionsForRole(role.id).stream().allMatch(principal::can);
    }

    private void assertCanGrantRole(Principal principal, SysRole role) {
        if (!canGrant(principal, role)) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                    "不能授予超出自身权限的角色：" + role.code);
        }
    }

    private Set<Long> allowedDeptIds(Principal principal) {
        if (principal.dataScope().isAll()) {
            return null;
        }
        if (principal.dataScope().deptIds().isEmpty()) {
            return Set.of();
        }
        return principal.dataScope().deptIds();
    }

    private long defaultDept(Principal principal) {
        if (principal.dataScope().isAll()) {
            return departmentRepository.findAll().stream()
                    .min(Comparator.comparing(d -> d.sortOrder))
                    .map(d -> d.id).orElse(1L);
        }
        return principal.dataScope().deptIds().stream().findFirst().orElse(1L);
    }

    private void requireDeptInScope(Principal principal, long deptId) {
        if (departmentRepository.findById(deptId).isEmpty()) {
            throw new AuroraException(AuroraErrorCode.NOT_FOUND, "部门不存在：" + deptId);
        }
        Set<Long> allowed = allowedDeptIds(principal);
        if (allowed != null && !allowed.contains(deptId)) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                    "目标部门不在当前数据范围内");
        }
    }

    public String roleName(String code) {
        return roleRepository.findByCode(code).map(role -> role.name).orElse(code);
    }

    public String departmentName(long deptId) {
        return departmentRepository.findById(deptId).map(dept -> dept.name)
                .orElse("部门 #" + deptId);
    }

    public Optional<SysUser> rawUser(String username) {
        return userRepository.findByUsername(username);
    }

    private List<SysUser> scopedCandidates(Principal principal) {
        DataScope scope = principal.dataScope();
        if (scope.isAll()) {
            return userRepository.findAll();
        }
        if (scope.isSelfOnly()) {
            return userRepository.findById(principal.userId()).map(List::of).orElseGet(List::of);
        }
        if (scope.deptIds().isEmpty()) {
            return List.of();
        }
        return userRepository.findByDeptIdIn(scope.deptIds());
    }

    private void requireInScope(Principal principal, SysUser user) {
        DataScope scope = principal.dataScope();
        boolean allowed = switch (scope.type()) {
            case ALL -> true;
            case SELF -> user.id == principal.userId();
            case DEPT, DEPT_AND_CHILD, CUSTOM -> scope.deptIds().contains(user.deptId);
        };
        if (!allowed) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                    "目标用户不在当前数据范围内");
        }
    }

    private RbacViews.UserView toView(SysUser user, Map<Long, String> deptNames,
                                      Map<Long, List<String>> rolesByUser) {
        return new RbacViews.UserView(user.id, user.username, user.displayName, user.title,
                user.deptId, deptNames.getOrDefault(user.deptId, "未知部门"),
                user.status, rolesByUser.getOrDefault(user.id, List.of()), user.avatarHue);
    }

    private Map<Long, String> departmentNames() {
        Map<Long, String> names = new HashMap<>();
        for (SysDepartment dept : departmentRepository.findAll()) {
            names.put(dept.id, dept.name);
        }
        return names;
    }

    private Map<Long, List<String>> rolesByUser() {
        Map<Long, SysRole> roles = new HashMap<>();
        for (SysRole role : roleRepository.findAll()) {
            roles.put(role.id, role);
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (SysUserRole link : userRoleRepository.findAll()) {
            SysRole role = roles.get(link.roleId);
            if (role != null) {
                result.computeIfAbsent(link.userId, k -> new ArrayList<>()).add(role.name);
            }
        }
        return result;
    }

    public record AssignResult(long userId, String username, String displayName,
                               String roleCode, String roleName, boolean changed) {
    }

    public record DeleteResult(long userId, String username, String displayName, int rolesRemoved) {
    }

    public record DepartmentOption(long id, String name, Long parentId) {
    }

    public record RoleOption(String code, String name, String description, boolean grantable) {
    }

    public record UserMeta(List<DepartmentOption> departments, List<RoleOption> roles,
                           String scopeType) {
    }

    public record UserPage(List<RbacViews.UserView> items, long total, int page, int size,
                           int totalPages) {
    }
}
