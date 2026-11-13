package io.aurora.app.web;

import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.ApiResponse;
import io.aurora.rbac.AuthService;
import io.aurora.rbac.PermissionService;
import io.aurora.rbac.Principal;
import io.aurora.rbac.RbacViews;
import io.aurora.retrieval.RetrievalService;
import io.aurora.tool.PluginDescriptor;
import io.aurora.tool.PluginService;
import io.aurora.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/desktop")
public class DesktopController {

    private final AuthService authService;
    private final PermissionService permissionService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final ToolRegistry toolRegistry;
    private final PluginService pluginService;
    private final RetrievalService retrievalService;

    public DesktopController(AuthService authService, PermissionService permissionService,
                             ApprovalService approvalService, AuditService auditService,
                             ToolRegistry toolRegistry, PluginService pluginService,
                             RetrievalService retrievalService) {
        this.authService = authService;
        this.permissionService = permissionService;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.toolRegistry = toolRegistry;
        this.pluginService = pluginService;
        this.retrievalService = retrievalService;
    }

    @GetMapping
    public ApiResponse<DesktopView> bootstrap(@RequestAttribute Principal principal,
                                              @RequestAttribute("token") String token) {
        RbacViews.SessionView session = authService.session(token);
        List<RbacViews.MenuItem> menu = permissionService.fullMenu(principal).stream()
                .map(this::menu)
                .toList();
        List<PluginDescriptor.WidgetMount> mounts = pluginService.activeMounts(principal);
        long pending = principal.can("approval:read") ? approvalService.pendingCount() : 0;
        long audits = principal.can("audit:read") ? auditService.count() : 0;
        int tools = principal.can("tool:read") ? toolRegistry.size() : 0;
        long activePlugins = pluginService.list().stream()
                .filter(p -> "ACTIVE".equals(p.status())).count();
        Stats stats = new Stats(pending, tools, audits,
                retrievalService.consistency().vectorChunks(),
                retrievalService.consistency().documents(),
                activePlugins, pluginService.list().size());
        return ApiResponse.ok(new DesktopView(session.principal(), session.activeRole(),
                session.availableRoles(), menu, mounts, stats, retrievalService.consistency(),
                "Aurora 1.0.0"));
    }

    private RbacViews.MenuItem menu(PermissionService.MenuItem item) {
        return new RbacViews.MenuItem(item.code(), item.name(), item.icon(),
                item.children().stream().map(this::menu).toList());
    }

    public record Stats(long pendingApprovals, int tools, long auditEvents, long knowledgeChunks,
                        long documents, long activePlugins, long plugins) {
    }

    public record DesktopView(RbacViews.PrincipalView principal, String activeRole,
                              List<RbacViews.RoleView> roles, List<RbacViews.MenuItem> menu,
                              List<PluginDescriptor.WidgetMount> mounts, Stats stats,
                              RetrievalService.Consistency consistency, String version) {
    }
}
