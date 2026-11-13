package io.aurora.rbac;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.rbac.domain.SysRole;
import io.aurora.rbac.domain.SysSession;
import io.aurora.rbac.domain.SysUser;
import io.aurora.rbac.domain.SysUserRole;
import io.aurora.rbac.repo.SysRoleRepository;
import io.aurora.rbac.repo.SysSessionRepository;
import io.aurora.rbac.repo.SysUserRepository;
import io.aurora.rbac.repo.SysUserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService implements PrincipalLoader {

    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final int TOKEN_BYTES = 32;

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysSessionRepository sessionRepository;
    private final PermissionService permissionService;
    private final DataScopeResolver dataScopeResolver;
    private final PasswordHasher passwordHasher;
    private final SecureRandom random = new SecureRandom();

    public AuthService(SysUserRepository userRepository,
                       SysRoleRepository roleRepository,
                       SysUserRoleRepository userRoleRepository,
                       SysSessionRepository sessionRepository,
                       PermissionService permissionService,
                       DataScopeResolver dataScopeResolver,
                       PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.sessionRepository = sessionRepository;
        this.permissionService = permissionService;
        this.dataScopeResolver = dataScopeResolver;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public LoginResult login(String username, String password) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.UNAUTHORIZED, "用户名或密码错误"));
        if (!"ACTIVE".equals(user.status) || !passwordHasher.matches(password, user.passwordHash)) {
            throw new AuroraException(AuroraErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        List<SysRole> roles = rolesOf(user.id);
        if (roles.isEmpty()) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "当前用户没有可用的角色");
        }
        SysRole activeRole = roles.stream()
                .min(Comparator.comparingInt((SysRole r) -> "admin".equals(r.code) ? 0 : 1))
                .orElseThrow();

        String rawToken = newToken();
        SysSession session = new SysSession();
        session.id = java.util.UUID.randomUUID().toString();
        session.tokenHash = Json.sha256(rawToken);
        session.userId = user.id;
        session.activeRoleId = activeRole.id;
        session.createdAt = Instant.now();
        session.lastSeenAt = session.createdAt;
        session.expiresAt = session.createdAt.plus(SESSION_TTL);
        sessionRepository.save(session);

        return new LoginResult(rawToken, session.expiresAt, principalOf(user, activeRole),
                roles.stream().map(r -> toView(r, user)).toList());
    }

    public Principal resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuroraException(AuroraErrorCode.UNAUTHORIZED, "缺少访问令牌");
        }
        SysSession session = sessionRepository.findByTokenHash(Json.sha256(rawToken))
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.UNAUTHORIZED, "会话无效"));
        if (session.expiresAt.isBefore(Instant.now())) {
            throw new AuroraException(AuroraErrorCode.UNAUTHORIZED, "会话已过期");
        }
        SysUser user = userRepository.findById(session.userId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.UNAUTHORIZED, "用户不存在"));
        if (!"ACTIVE".equals(user.status)) {
            throw new AuroraException(AuroraErrorCode.UNAUTHORIZED, "用户已停用");
        }
        SysRole activeRole = roleRepository.findById(session.activeRoleId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.UNAUTHORIZED, "角色不存在"));
        return principalOf(user, activeRole);
    }

    public RbacViews.SessionView session(String rawToken) {
        SysSession session = requireSession(rawToken);
        SysUser user = userRepository.findById(session.userId).orElseThrow();
        SysRole active = roleRepository.findById(session.activeRoleId).orElseThrow();
        return new RbacViews.SessionView(RbacViews.PrincipalView.from(principalOf(user, active)),
                rolesOf(user.id).stream().map(r -> toView(r, user)).toList(),
                session.expiresAt, active.code);
    }

    @Transactional
    public RbacViews.SessionView switchRole(String rawToken, String roleCode) {
        SysSession session = requireSession(rawToken);
        SysUser user = userRepository.findById(session.userId).orElseThrow();
        List<SysRole> roles = rolesOf(user.id);
        SysRole target = roles.stream().filter(r -> r.code.equals(roleCode)).findFirst()
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.PERMISSION_DENIED,
                        "当前用户不拥有角色 " + roleCode));
        session.activeRoleId = target.id;
        sessionRepository.save(session);
        return new RbacViews.SessionView(RbacViews.PrincipalView.from(principalOf(user, target)),
                roles.stream().map(r -> toView(r, user)).toList(),
                session.expiresAt, target.code);
    }

    @Transactional
    public void logout(String rawToken) {
        sessionRepository.findByTokenHash(Json.sha256(rawToken)).ifPresent(sessionRepository::delete);
    }

    public List<SysRole> rolesOf(long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.roleId))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(r -> r.code))
                .toList();
    }

    public List<RbacViews.RoleView> availableRolesView(long userId) {
        SysUser user = userRepository.findById(userId).orElseThrow();
        return rolesOf(userId).stream().map(r -> toView(r, user)).toList();
    }

    @Override
    public Principal load(long userId, long roleId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.UNAUTHORIZED, "用户不存在"));
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.UNAUTHORIZED, "角色不存在"));
        if (!"ACTIVE".equals(user.status)) {
            throw new AuroraException(AuroraErrorCode.UNAUTHORIZED, "用户已停用");
        }
        return principalOf(user, role);
    }

    private Principal principalOf(SysUser user, SysRole role) {
        Set<String> permissions = new HashSet<>(permissionService.permissionsForRole(role.id));
        if ("admin".equals(role.code)) {
            permissions.add("*");
        }
        DataScope scope = dataScopeResolver.resolve(role.id, user.deptId);
        return new Principal(user.id, user.username, user.displayName, role.name,
                Set.of(role.code), Set.of(role.id), scope, 0L, permissions,
                user.avatarHue);
    }

    private RbacViews.RoleView toView(SysRole role, SysUser user) {
        DataScope scope = dataScopeResolver.resolve(role.id, user.deptId);
        return new RbacViews.RoleView(role.id, role.code, role.name, role.description, scope.type().name());
    }

    private SysSession requireSession(String rawToken) {
        SysSession session = sessionRepository.findByTokenHash(Json.sha256(rawToken))
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.UNAUTHORIZED, "会话无效"));
        if (session.expiresAt.isBefore(Instant.now())) {
            throw new AuroraException(AuroraErrorCode.UNAUTHORIZED, "会话已过期");
        }
        return session;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record LoginResult(String token, Instant expiresAt, Principal principal,
                              List<RbacViews.RoleView> roles) {
    }

}
