package io.aurora.rbac;

import io.aurora.rbac.domain.SysDataScopeRule;
import io.aurora.rbac.domain.SysDepartment;
import io.aurora.rbac.repo.SysDataScopeRuleRepository;
import io.aurora.rbac.repo.SysDepartmentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a role's data scope, including department subtrees.
 */
@Service
public class DataScopeResolver {

    private final SysDataScopeRuleRepository ruleRepository;
    private final SysDepartmentRepository departmentRepository;

    public DataScopeResolver(SysDataScopeRuleRepository ruleRepository,
                             SysDepartmentRepository departmentRepository) {
        this.ruleRepository = ruleRepository;
        this.departmentRepository = departmentRepository;
    }

    public DataScope resolve(long roleId, long userDeptId) {
        List<SysDataScopeRule> rules = ruleRepository.findByRoleId(roleId);
        if (rules.isEmpty()) {
            return new DataScope(DataScope.Type.SELF, Set.of());
        }
        SysDataScopeRule rule = rules.get(0);
        return switch (DataScope.Type.valueOf(rule.type)) {
            case ALL -> DataScope.all();
            case DEPT -> new DataScope(DataScope.Type.DEPT, Set.of(userDeptId));
            case DEPT_AND_CHILD -> new DataScope(DataScope.Type.DEPT_AND_CHILD, subtree(userDeptId));
            case SELF -> new DataScope(DataScope.Type.SELF, Set.of());
            case CUSTOM -> new DataScope(DataScope.Type.CUSTOM, parseIds(rule.customDeptIds));
        };
    }

    public Set<Long> subtree(long rootDeptId) {
        List<SysDepartment> all = departmentRepository.findAll();
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootDeptId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!result.add(current)) {
                continue;
            }
            for (SysDepartment dept : all) {
                if (current.equals(dept.parentId)) {
                    queue.add(dept.id);
                }
            }
        }
        return Set.copyOf(result);
    }

    private Set<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Skip malformed entries; conservative resolution drops them.
            }
        }
        return Set.copyOf(ids);
    }
}
