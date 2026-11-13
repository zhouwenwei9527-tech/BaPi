package io.aurora.rbac;

/**
 * The only source of truth for whether a permission code exists.
 * Tool registry depends on this abstraction, never on a concrete RBAC class.
 */
public interface PermissionCatalog {
    boolean exists(String code);
}
