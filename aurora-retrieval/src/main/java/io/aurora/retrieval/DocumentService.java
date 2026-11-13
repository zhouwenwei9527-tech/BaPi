package io.aurora.retrieval;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Document read model with current-ACL checks. This is the secondary check
 * that makes ACL changes effective on the very next search, even when vector
 * payloads have not been rewritten yet (PRD §5.6, AC-11).
 */
@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final StoragePort storage;

    public DocumentService(DocumentRepository documents, StoragePort storage) {
        this.documents = documents;
        this.storage = storage;
    }

    public List<DocumentRecord> visible(Principal principal, String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return documents.findAll().stream()
                .filter(d -> visibleNow(principal, d))
                .filter(d -> needle.isEmpty()
                        || d.title().toLowerCase(Locale.ROOT).contains(needle)
                        || d.summary().toLowerCase(Locale.ROOT).contains(needle)
                        || d.fileName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(DocumentRecord::createdAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public DocumentRecord requireVisible(Principal principal, String id) {
        DocumentRecord doc = documents.findById(id)
                .orElseThrow(() -> new AuroraException(AuroraErrorCode.NOT_FOUND, "文档不存在"));
        if (!visibleNow(principal, doc)) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "无权访问该文档");
        }
        return doc;
    }

    public String fileUrl(Principal principal, String id, Duration ttl) {
        DocumentRecord doc = requireVisible(principal, id);
        return storage.presign(doc.storageKey(), ttl);
    }

    public boolean visibleNow(Principal principal, DocumentRecord doc) {
        if (!principal.can(doc.requiredPermission())) {
            return false;
        }
        return switch (doc.visibility()) {
            case "PUBLIC" -> true;
            case "ROLES" -> doc.visibleRoles().stream().anyMatch(principal.roles()::contains);
            case "DEPTS" -> principal.dataScope().isAll()
                    || doc.visibleDepts().stream().anyMatch(principal.dataScope().deptIds()::contains);
            case "OWNER" -> doc.ownerId() == principal.userId();
            default -> false;
        };
    }

    public List<DocumentRecord> all() {
        return documents.findAll();
    }

    public List<DocumentRecord> byIds(Principal principal, List<String> ids) {
        List<DocumentRecord> out = new ArrayList<>();
        for (String id : ids) {
            documents.findById(id).filter(d -> visibleNow(principal, d)).ifPresent(out::add);
        }
        return out;
    }

    public StoragePort storage() {
        return storage;
    }
}
