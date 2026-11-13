package io.aurora.app.web;

import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.Json;
import io.aurora.contract.UnifiedResult;
import io.aurora.rbac.Principal;
import io.aurora.retrieval.RetrievalRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AuroraRetrievalRecorder implements RetrievalRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuroraRetrievalRecorder.class);

    private final AuditService auditService;

    public AuroraRetrievalRecorder(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(Principal principal, String query, boolean imageQuery,
                       List<UnifiedResult> results, int trimmed) {
        List<Map<String, String>> refs = new ArrayList<>();
        results.stream().limit(8).forEach(r -> {
            if (r.sourceRef() != null) {
                refs.add(Map.of("objectType", r.sourceRef().objectType(),
                        "objectId", r.sourceRef().objectId()));
            }
        });
        try {
            auditService.record(AuditDraft.of(AuditService.TYPE_RETRIEVAL)
                    .trace("ret-" + java.util.UUID.randomUUID()).session("search")
                    .actor(io.aurora.contract.Actor.user())
                    .onBehalfOf(principal)
                    .argsDigest(Json.sha256(query == null ? "" : query))
                    .decision(imageQuery ? "IMAGE_QUERY" : "TEXT_QUERY")
                    .status("OK")
                    .refs(refs));
        } catch (RuntimeException e) {
            log.warn("Retrieval audit skipped: {}", e.getMessage());
        }
    }
}
