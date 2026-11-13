package io.aurora.rbac;

import java.util.Set;

/**
 * Resolved data visibility for a principal. Mirrors sys_data_scope_rule.
 */
public record DataScope(Type type, Set<Long> deptIds) {

    public enum Type { ALL, DEPT, DEPT_AND_CHILD, SELF, CUSTOM }

    public DataScope {
        deptIds = Set.copyOf(deptIds);
    }

    public static DataScope all() {
        return new DataScope(Type.ALL, Set.of());
    }

    public boolean isAll() {
        return type == Type.ALL;
    }

    public boolean isSelfOnly() {
        return type == Type.SELF;
    }
}
