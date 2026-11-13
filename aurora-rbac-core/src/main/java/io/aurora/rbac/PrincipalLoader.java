package io.aurora.rbac;

/**
 * Re-loads a principal for re-validation at execution time (INV-3).
 */
public interface PrincipalLoader {
    Principal load(long userId, long roleId);
}
