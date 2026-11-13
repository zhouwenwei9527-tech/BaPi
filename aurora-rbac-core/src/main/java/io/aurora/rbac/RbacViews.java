package io.aurora.rbac;

import java.time.Instant;
import java.util.List;

/**
 * Read models exposed by the RBAC module.
 */
public final class RbacViews {

    private RbacViews() {
    }

    public record PrincipalView(
            long id,
            String username,
            String displayName,
            String title,
            List<String> roles,
            List<String> permissions,
            DataScope dataScope,
            int avatarHue
    ) {
        public static PrincipalView from(Principal p) {
            return new PrincipalView(p.userId(), p.username(), p.displayName(), p.title(),
                    p.roles().stream().sorted().toList(), p.permissionList(), p.dataScope(), p.avatarHue());
        }
    }

    public record RoleView(long id, String code, String name, String description, String scopeType) {
    }

    public record SessionView(PrincipalView principal, List<RoleView> availableRoles,
                              Instant expiresAt, String activeRole) {
    }

    public record UserView(long id, String username, String displayName, String title,
                           long deptId, String deptName, String status, List<String> roles,
                           int avatarHue) {
    }

    public record MenuItem(String code, String name, String icon, List<MenuItem> children) {
    }

    public record MatrixRow(String code, String name, String type, List<String> roles) {
    }
}
