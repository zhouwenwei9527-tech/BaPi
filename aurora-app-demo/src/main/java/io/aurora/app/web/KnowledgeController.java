package io.aurora.app.web;

import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.rbac.PermissionCatalog;
import io.aurora.rbac.Principal;
import io.aurora.retrieval.DocumentRecord;
import io.aurora.retrieval.DocumentRepository;
import io.aurora.retrieval.DocumentService;
import io.aurora.retrieval.RetrievalService;
import io.aurora.retrieval.StoragePort;
import io.aurora.retrieval.VectorIndexer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class KnowledgeController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final VectorIndexer vectorIndexer;
    private final RetrievalService retrievalService;
    private final PermissionCatalog permissionCatalog;
    private final StoragePort storage;
    private final AuditService auditService;

    public KnowledgeController(DocumentService documentService, DocumentRepository documentRepository,
                               VectorIndexer vectorIndexer, RetrievalService retrievalService,
                               PermissionCatalog permissionCatalog, StoragePort storage,
                               AuditService auditService) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.vectorIndexer = vectorIndexer;
        this.retrievalService = retrievalService;
        this.permissionCatalog = permissionCatalog;
        this.storage = storage;
        this.auditService = auditService;
    }

    @GetMapping("/knowledge")
    public ApiResponse<List<DocumentView>> list(@RequestAttribute Principal principal) {
        return ApiResponse.ok(documentService.visible(principal, "", 100).stream()
                .map(this::view).toList());
    }

    @GetMapping("/knowledge/{id}")
    public ApiResponse<DocumentDetail> detail(@RequestAttribute Principal principal,
                                              @PathVariable String id) {
        DocumentRecord doc = documentService.requireVisible(principal, id);
        String url = documentService.storage().presign(doc.storageKey(), Duration.ofMinutes(5));
        return ApiResponse.ok(new DocumentDetail(view(doc), truncate(doc.content(), 4000), url,
                documentService.storage().provider()));
    }

    /**
     * Human data-entry path: file bytes -> MinIO, metadata -> MySQL, embedding
     * -> pgvector, so the next search can find it immediately.
     */
    @PostMapping(value = "/knowledge/upload", consumes = "multipart/form-data")
    public ApiResponse<UploadResult> upload(@RequestAttribute Principal principal,
                                            @org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file,
                                            @org.springframework.web.bind.annotation.RequestParam(value = "title", defaultValue = "") String title,
                                            @org.springframework.web.bind.annotation.RequestParam(value = "summary", defaultValue = "") String summary,
                                            @org.springframework.web.bind.annotation.RequestParam(value = "visibility", defaultValue = "OWNER") String visibility,
                                            @org.springframework.web.bind.annotation.RequestParam(value = "visibleRoles", defaultValue = "") String visibleRoles,
                                            @org.springframework.web.bind.annotation.RequestParam(value = "visibleDepts", defaultValue = "") String visibleDepts,
                                            @org.springframework.web.bind.annotation.RequestParam(value = "requiredPermission", defaultValue = "knowledge:internal") String requiredPermission) {
        if (!principal.can("knowledge:manage")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 knowledge:manage 权限");
        }
        if (file == null || file.isEmpty()) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "文件不能超过 10MB");
        }
        String originalName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String fileName = originalName.replaceAll(".*[/\\\\]", "").replaceAll("[^a-zA-Z0-9._\\u4e00-\\u9fa5-]", "_");
        String extension = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        Set<String> allowed = Set.of("pdf", "docx", "xlsx", "txt", "md", "csv", "json", "png", "jpg", "jpeg", "webp");
        if (!allowed.contains(extension)) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST,
                    "不支持的文件类型：" + (extension.isEmpty() ? "未知" : extension));
        }
        Set<String> allowedVisibility = Set.of("PUBLIC", "ROLES", "DEPTS", "OWNER");
        if (!allowedVisibility.contains(visibility)) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "visibility 不合法");
        }
        if (!permissionCatalog.exists(requiredPermission)) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "权限码不存在：" + requiredPermission);
        }
        if (!storage.available()) {
            throw new AuroraException(AuroraErrorCode.TOOL_FAILED, "文件服务不可用，无法上传文件");
        }

        String id = UUID.randomUUID().toString();
        String safeTitle = title == null || title.isBlank() ? fileName : title.trim();
        try {
            byte[] bytes = file.getBytes();
            String content;
            if (extension.equals("txt") || extension.equals("md") || extension.equals("csv")
                    || extension.equals("json")) {
                content = new String(bytes, StandardCharsets.UTF_8);
                if (content.length() > 20000) {
                    content = content.substring(0, 20000);
                }
            } else {
                content = (summary == null ? "" : summary) + "\n[上传文件：" + fileName + "]";
            }
            String storageKey = "uploads/" + id + "-" + fileName;
            storage.put(storageKey, bytes, file.getContentType());
            String safeSummary = summary == null || summary.isBlank()
                    ? content.substring(0, Math.min(160, content.length())) : summary;
            DocumentRecord doc = new DocumentRecord(id, safeTitle, safeSummary, content,
                    "upload", id, principal.userId(), visibility, csv(visibleRoles), deptSet(visibleDepts),
                    1, 1, 1, requiredPermission, storageKey,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    fileName, file.getSize(), Instant.now());
            documentRepository.insert(doc);
            vectorIndexer.reindex(doc);
            try {
                auditService.record(AuditDraft.of(AuditService.TYPE_RETRIEVAL)
                        .trace("upload-" + UUID.randomUUID()).session("knowledge")
                        .actor(io.aurora.contract.Actor.user()).onBehalfOf(principal)
                        .argsDigest(Json.sha256(safeTitle)).decision("UPLOAD").status("OK")
                        .refs(List.of(Map.of("objectType", "doc", "objectId", id))));
            } catch (AuroraException auditError) {
                // The document is already stored; surface index state and let the
                // audit failure be handled by observability.
            }
            return ApiResponse.ok(new UploadResult(view(doc), true, storage.provider(),
                    "文件已上传，元数据入库并重建向量索引"));
        } catch (java.io.IOException e) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "无法读取上传文件", e);
        }
    }

    @GetMapping("/files/{id}/url")
    public ApiResponse<FileUrl> fileUrl(@RequestAttribute Principal principal,
                                        @PathVariable String id) {
        DocumentRecord doc = documentService.requireVisible(principal, id);
        String url = documentService.fileUrl(principal, id, Duration.ofMinutes(5));
        return ApiResponse.ok(new FileUrl(doc.id(), doc.fileName(), url, 5,
                url == null ? "文件服务不可用" : "短时效安全链接"));
    }

    /** ACL change takes effect on the next search via the MySQL second pass (AC-11). */
    @PatchMapping("/knowledge/{id}/acl")
    public ApiResponse<AclResult> updateAcl(@RequestAttribute Principal principal,
                                            @PathVariable String id,
                                            @RequestBody AclRequest request) {
        if (!principal.can("knowledge:manage")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 knowledge:manage 权限");
        }
        Set<String> allowed = Set.of("PUBLIC", "ROLES", "DEPTS", "OWNER");
        if (!allowed.contains(request.visibility())) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "visibility 不合法");
        }
        if (request.requiredPermission() != null && !request.requiredPermission().isBlank()
                && !permissionCatalog.exists(request.requiredPermission())) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "权限码不存在");
        }
        documentService.requireVisible(principal, id);
        documentRepository.updateAcl(id,
                request.visibility(),
                request.visibleRoles() == null ? "" : String.join(",", request.visibleRoles()),
                request.visibleDepts() == null ? "" : request.visibleDepts().stream()
                        .map(String::valueOf).collect(java.util.stream.Collectors.joining(",")),
                request.requiredPermission());
        documentRepository.findById(id).ifPresent(vectorIndexer::reindex);
        return ApiResponse.ok(new AclResult(id, documentRepository.aclVersion(id),
                "文档访问范围已更新，索引已同步；下一次检索立即生效"));
    }

    @PostMapping("/knowledge/reindex")
    public ApiResponse<IndexResult> reindex(@RequestAttribute Principal principal) {
        if (!principal.can("knowledge:manage")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 knowledge:manage 权限");
        }
        int count = vectorIndexer.reindexAll();
        return ApiResponse.ok(new IndexResult(count, retrievalService.consistency()));
    }

    private DocumentView view(DocumentRecord doc) {
        return new DocumentView(doc.id(), doc.title(), doc.summary(), doc.objectType(), doc.objectId(),
                doc.visibility(), doc.aclVersion(), doc.page(), doc.paragraph(),
                doc.requiredPermission(), doc.fileName(), doc.fileSize(), doc.mimeType(),
                doc.createdAt().toString());
    }

    private Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(String::trim)
                .filter(v -> !v.isEmpty()).collect(java.util.stream.Collectors.toSet());
    }

    private Set<Long> deptSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            return java.util.Arrays.stream(value.split(",")).map(String::trim)
                    .filter(v -> !v.isEmpty()).map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (NumberFormatException e) {
            throw new AuroraException(AuroraErrorCode.BAD_REQUEST, "visibleDepts 必须是逗号分隔的部门 ID");
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public record DocumentView(String id, String title, String summary, String objectType,
                               String objectId, String visibility, int aclVersion, int page,
                               int paragraph, String requiredPermission, String fileName,
                               long fileSize, String mimeType, String createdAt) {
    }

    public record DocumentDetail(DocumentView document, String content, String previewUrl,
                                 String storageProvider) {
    }

    public record FileUrl(String docId, String fileName, String url, int expiresInMinutes,
                          String note) {
    }

    public record AclRequest(String visibility, List<String> visibleRoles,
                             List<Long> visibleDepts, String requiredPermission) {
    }

    public record AclResult(String docId, int aclVersion, String note) {
    }

    public record IndexResult(int documents, RetrievalService.Consistency consistency) {
    }

    public record UploadResult(DocumentView document, boolean indexed, String storageProvider,
                               String message) {
    }
}
