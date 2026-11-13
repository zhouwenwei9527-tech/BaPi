package io.aurora.app.web;

import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.ApiResponse;
import io.aurora.rbac.Principal;
import io.aurora.retrieval.DocumentRepository;
import io.aurora.retrieval.RetrievalService;
import io.aurora.retrieval.StoragePort;
import io.aurora.retrieval.VectorStorePort;
import io.aurora.tool.PluginService;
import io.aurora.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final RetrievalService retrievalService;
    private final VectorStorePort vectorStore;
    private final StoragePort storage;
    private final ToolRegistry toolRegistry;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final PluginService pluginService;
    private final DocumentRepository documentRepository;

    public SystemController(RetrievalService retrievalService, VectorStorePort vectorStore,
                            StoragePort storage, ToolRegistry toolRegistry,
                            ApprovalService approvalService, AuditService auditService,
                            PluginService pluginService, DocumentRepository documentRepository) {
        this.retrievalService = retrievalService;
        this.vectorStore = vectorStore;
        this.storage = storage;
        this.toolRegistry = toolRegistry;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.pluginService = pluginService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("version", "1.0.0");
        body.put("time", Instant.now().toString());
        body.put("vectorStore", vectorStore.getClass().getSimpleName());
        body.put("storage", storage.provider());
        body.put("storageAvailable", storage.available());
        return ApiResponse.ok(body);
    }

    @GetMapping("/system/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestAttribute Principal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("principal", principal.displayName());
        body.put("tools", toolRegistry.size());
        body.put("pendingApprovals", principal.can("approval:read") ? approvalService.pendingCount() : 0);
        body.put("auditEvents", principal.can("audit:read") ? auditService.count() : 0);
        body.put("documents", documentRepository.count());
        body.put("vectorChunks", vectorStore.count());
        body.put("plugins", pluginService.list().size());
        body.put("consistency", retrievalService.consistency());
        body.put("storage", storage.provider());
        body.put("vectorStore", vectorStore.getClass().getSimpleName());
        return ApiResponse.ok(body);
    }
}
