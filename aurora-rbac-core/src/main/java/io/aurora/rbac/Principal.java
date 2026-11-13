package io.aurora.rbac;

import java.util.List;
import java.util.Set;

/**
 * The authenticated human on whose behalf every action runs.
 * Agent authority is a subset of this principal's authority (INV-3).
 */
public record Principal(
        long userId,
        String username,
        String displayName,
        String title,
        Set<String> roles,
        Set<Long> roleIds,
        DataScope dataScope,
        long tenantId,
        Set<String> permissions,
        int avatarHue
) {

    public Principal {
        roles = Set.copyOf(roles);
        roleIds = Set.copyOf(roleIds);
        permissions = Set.copyOf(permissions);
    }

    public boolean can(String permission) {
        return permission == null || permission.isBlank()
                || permissions.contains("*") || permissions.contains(permission);
    }

    public boolean isSuperAdmin() {
        return permissions.contains("*");
    }

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    public List<String> permissionList() {
        return permissions.stream().sorted().toList();
    }

    public Principal withPermissions(Set<String> newPermissions) {
        return new Principal(userId, username, displayName, title, roles, roleIds,
                dataScope, tenantId, newPermissions, avatarHue);
    }
}
