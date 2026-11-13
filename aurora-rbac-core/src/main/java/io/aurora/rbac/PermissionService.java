package io.aurora.rbac;

import io.aurora.rbac.domain.SysResource;
import io.aurora.rbac.repo.SysResourceRepository;
import io.aurora.rbac.repo.SysRoleResourceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Permission resolution with a 5-second cache (PRD §5.1).
 */
@Service
public class PermissionService implements PermissionCatalog {

    private static final long TTL_MS = 5_000;

    private final SysResourceRepository resourceRepository;
    private final SysRoleResourceRepository roleResourceRepository;
    private final Map<Long, CacheEntry> roleCache = new ConcurrentHashMap<>();

    public PermissionService(SysResourceRepository resourceRepository,
                             SysRoleResourceRepository roleResourceRepository) {
        this.resourceRepository = resourceRepository;
        this.roleResourceRepository = roleResourceRepository;
    }

    @Override
    public boolean exists(String code) {
        return resourceRepository.findByCode(code).isPresent();
    }

    public Set<String> permissionsForRole(long roleId) {
        CacheEntry cached = roleCache.get(roleId);
        if (cached != null && !cached.isExpired()) {
            return cached.codes;
        }
        Set<String> codes = loadRolePermissions(roleId);
        roleCache.put(roleId, new CacheEntry(codes, Instant.now().toEpochMilli() + TTL_MS));
        return codes;
    }

    public Set<String> permissionsForRoles(Set<Long> roleIds) {
        return roleIds.stream()
                .flatMap(id -> permissionsForRole(id).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> loadRolePermissions(long roleId) {
        List<Long> resourceIds = roleResourceRepository.findByRoleId(roleId).stream()
                .map(rr -> rr.resourceId)
                .toList();
        if (resourceIds.isEmpty()) {
            return Set.of();
        }
        return resourceRepository.findByIdIn(resourceIds).stream()
                .map(r -> r.code)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void invalidate() {
        roleCache.clear();
    }

    public List<SysResource> allResources() {
        return resourceRepository.findAllByOrderBySortOrderAsc();
    }

    /** Menu tree: only MENU resources visible to the principal. */
    public List<MenuItem> menuFor(Principal principal) {
        Map<String, List<SysResource>> byParent = allResources().stream()
                .filter(r -> "MENU".equals(r.type))
                .filter(r -> principal.can(r.code))
                .collect(Collectors.groupingBy(r -> r.parentCode == null ? "" : r.parentCode,
                        LinkedHashMap::new, Collectors.toList()));

        return byParent.entrySet().stream()
                .filter(e -> e.getKey().isEmpty())
                .flatMap(e -> e.getValue().stream())
                .map(root -> new MenuItem(root.code, root.name, root.icon, List.of()))
                .toList();
    }

    /** Full menu including declared groups (for the menu app). */
    public List<MenuItem> fullMenu(Principal principal) {
        List<SysResource> menus = allResources().stream()
                .filter(r -> "MENU".equals(r.type))
                .toList();
        Map<String, List<SysResource>> children = menus.stream()
                .filter(r -> r.parentCode != null && !r.parentCode.isBlank())
                .collect(Collectors.groupingBy(r -> r.parentCode, LinkedHashMap::new, Collectors.toList()));

        return menus.stream()
                .filter(r -> r.parentCode == null || r.parentCode.isBlank())
                .sorted(Comparator.comparingInt(r -> r.sortOrder))
                .map(group -> new MenuItem(group.code, group.name, group.icon,
                        children.getOrDefault(group.code, List.of()).stream()
                                .filter(child -> principal.can(child.code))
                                .sorted(Comparator.comparingInt(r -> r.sortOrder))
                                .map(child -> new MenuItem(child.code, child.name, child.icon, List.of()))
                                .toList()))
                .filter(group -> !group.children().isEmpty())
                .toList();
    }

    public record MenuItem(String code, String name, String icon, List<MenuItem> children) {
    }

    private record CacheEntry(Set<String> codes, long expiresAt) {
        boolean isExpired() {
            return Instant.now().toEpochMilli() > expiresAt;
        }
    }
}
