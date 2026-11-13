package io.aurora.app.preference;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dock order is account-level state. The server keeps a canonical allow-list
 * so a tampered client cannot persist unknown entries.
 */
@Service
public class DockPreferenceService {

    private static final List<String> KNOWN_APPS = List.of(
            "finder", "directory", "agent", "approvals", "audit", "tools",
            "plugins", "access", "guide", "rbac", "glass");
    private static final Set<String> KNOWN = Set.copyOf(KNOWN_APPS);
    private static final int MAX_APPS = KNOWN_APPS.size();

    private final UserPreferenceRepository repository;

    public DockPreferenceService(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    public List<String> get(long userId) {
        return repository.findDockApps(userId)
                .map(this::parse)
                .filter(apps -> !apps.isEmpty())
                .orElseGet(DockPreferenceService::defaultApps);
    }

    public List<String> save(long userId, List<String> requested) {
        List<String> sanitized = sanitize(requested);
        repository.saveDockApps(userId, String.join(",", sanitized));
        return sanitized;
    }

    private List<String> sanitize(List<String> requested) {
        LinkedHashSet<String> apps = new LinkedHashSet<>();
        if (requested != null) {
            requested.stream()
                    .filter(app -> app != null && KNOWN.contains(app))
                    .limit(MAX_APPS)
                    .forEach(apps::add);
        }
        apps.remove("finder");
        List<String> ordered = new ArrayList<>();
        ordered.add("finder");
        ordered.addAll(apps);
        return List.copyOf(ordered);
    }

    private List<String> parse(String csv) {
        List<String> apps = csv == null ? List.of() : List.of(csv.split(","));
        return sanitize(apps);
    }

    private static List<String> defaultApps() {
        return List.copyOf(KNOWN_APPS);
    }
}
