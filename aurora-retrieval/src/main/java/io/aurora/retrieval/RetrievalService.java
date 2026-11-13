package io.aurora.retrieval;

import io.aurora.contract.Json;
import io.aurora.contract.UnifiedResult;
import io.aurora.rbac.Principal;
import io.aurora.rbac.RbacViews;
import io.aurora.rbac.UserQueryService;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Unified retrieval: ACTION + ENTITY + KNOWLEDGE in one response, with ACL
 * filtering performed before recall and a current-ACL second pass (AC-10/11/12).
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStorePort vectorStore;
    private final EmbeddingPort embedding;
    private final DocumentService documentService;
    private final DocumentRepository documents;
    private final UserQueryService userQueryService;
    private final ObjectProvider<ActionCatalog> actionCatalog;
    private final ObjectProvider<RetrievalRecorder> recorder;
    private final int overFetch;

    public RetrievalService(VectorStorePort vectorStore, EmbeddingPort embedding,
                            DocumentService documentService, DocumentRepository documents,
                            UserQueryService userQueryService,
                            ObjectProvider<ActionCatalog> actionCatalog,
                            ObjectProvider<RetrievalRecorder> recorder,
                            @Value("${aurora.retrieval.overFetch:2}") int overFetch) {
        this.vectorStore = vectorStore;
        this.embedding = embedding;
        this.documentService = documentService;
        this.documents = documents;
        this.userQueryService = userQueryService;
        this.actionCatalog = actionCatalog;
        this.recorder = recorder;
        this.overFetch = Math.max(1, overFetch);
    }

    public SearchResult search(Principal principal, String query, byte[] image, String caption,
                               String fileName, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 30));
        String effectiveQuery = query == null ? "" : query.trim();
        boolean imageQuery = image != null && image.length > 0;
        float[] vector = imageQuery
                ? embedding.embedImage(image, (caption == null ? "" : caption) + " " + effectiveQuery,
                        fileName)
                : embedding.embedText(effectiveQuery);

        int trimmed = 0;
        List<UnifiedResult> results = new ArrayList<>();
        Set<String> seenDocIds = new HashSet<>();

        // Knowledge: over-fetch ×2, then re-check the current MySQL ACL.
        List<VectorHit> hits = vectorStore.search(new VectorQuery(0L, vector,
                safeLimit * overFetch, principal));
        for (VectorHit hit : hits) {
            DocumentRecord doc = documents.findById(hit.record().docId()).orElse(null);
            if (doc == null || !documentService.visibleNow(principal, doc)) {
                trimmed++;
                continue;
            }
            if (!seenDocIds.add(doc.id())) {
                continue;
            }
            double score = blendedScore(effectiveQuery, hit.score(), doc);
            results.add(UnifiedResult.knowledge(
                    doc.title(),
                    "《" + doc.title() + "》第 " + doc.page() + " 页 · " + truncate(doc.summary(), 80),
                    score, doc.requiredPermission(),
                    "doc", doc.id(), doc.page(), doc.paragraph()));
        }

        // Files are entities too; one row per document, deduplicated with knowledge.
        if (principal.can("file:read")) {
            for (DocumentRecord doc : documentService.visible(principal, effectiveQuery, safeLimit)) {
                results.add(UnifiedResult.entity(doc.fileName(),
                        "文件 · " + humanSize(doc.fileSize()) + " · " + doc.objectType(),
                        0.66, doc.requiredPermission(),
                        "file", doc.objectId(),
                        Map.<String, Object>of("docId", doc.id(), "mimeType", doc.mimeType(),
                                "summary", doc.summary())));
            }
        }

        // People (data scope applied by the framework data layer).
        if (principal.can("user:read") && !effectiveQuery.isBlank()) {
            List<RbacViews.UserView> users = userQueryService.search(principal, effectiveQuery, 5);
            for (RbacViews.UserView user : users) {
                results.add(UnifiedResult.entity(user.displayName(),
                        "用户 · " + user.deptName() + " · " + user.title(), 0.72,
                        "user:read", "user", String.valueOf(user.id()),
                        Map.<String, Object>of("username", user.username(),
                                "deptName", user.deptName(), "status", user.status())));
            }
        }

        // Actions declared by the shell.
        ActionCatalog catalog = actionCatalog.getIfAvailable();
        if (catalog != null) {
            for (UnifiedResult action : catalog.actions(principal)) {
                if (principal.can(action.requiredPermission())) {
                    results.add(action);
                }
            }
        }

        List<UnifiedResult> ranked = dedupe(results).stream()
                .sorted(Comparator.comparingDouble(UnifiedResult::score).reversed()
                        .thenComparing(r -> typeOrder(r.type())))
                .toList();
        // Keep the palette genuinely mixed: quotas per kind, then fill by score.
        int perKind = Math.max(2, (int) Math.ceil(safeLimit / 3.0));
        List<UnifiedResult> combined = new ArrayList<>();
        for (UnifiedResult.Kind kind : List.of(UnifiedResult.Kind.ACTION,
                UnifiedResult.Kind.ENTITY, UnifiedResult.Kind.KNOWLEDGE)) {
            ranked.stream().filter(r -> r.type() == kind).limit(perKind).forEach(combined::add);
        }
        for (UnifiedResult result : ranked) {
            if (combined.size() >= safeLimit) {
                break;
            }
            if (!combined.contains(result)) {
                combined.add(result);
            }
        }
        combined = combined.stream()
                .sorted(Comparator.comparingDouble(UnifiedResult::score).reversed()
                        .thenComparing(r -> typeOrder(r.type())))
                .limit(safeLimit)
                .toList();

        RetrievalRecorder auditRecorder = recorder.getIfAvailable();
        if (auditRecorder != null) {
            auditRecorder.record(principal, effectiveQuery, imageQuery, combined, trimmed);
        }

        return new SearchResult(combined, trimmed, imageQuery, embedding.provider(),
                vectorStore.count(), documents.count(), consistency());
    }

    @Scheduled(fixedDelayString = "${aurora.retrieval.patrolIntervalMs:60000}",
            initialDelayString = "${aurora.retrieval.patrolIntervalMs:60000}")
    public void patrolIndexConsistency() {
        Consistency current = consistency();
        if (current.alert()) {
            log.warn("Vector index drift: documents={} chunks={} deviation={} — {}",
                    current.documents(), current.vectorChunks(), current.deviation(),
                    current.message());
        }
    }

    public Consistency consistency() {
        long docs = documents.count();
        long chunks = vectorStore.count();
        double deviation = docs == 0 ? 0 : Math.abs(chunks - docs) / (double) docs;
        boolean alert = docs > 0 && deviation > 0.01;
        return new Consistency(docs, chunks, round(deviation), alert,
                alert ? "文档与索引计数偏差超过 1%，请检查索引一致性"
                        : "索引与源数据一致");
    }

    public int reindex() {
        return documentService.all().size();
    }

    public List<UnifiedResult> knowledgeOnly(Principal principal, String query, int limit) {
        return search(principal, query, null, null, null, limit).results().stream()
                .filter(r -> r.type() == UnifiedResult.Kind.KNOWLEDGE)
                .toList();
    }

    private List<UnifiedResult> dedupe(List<UnifiedResult> results) {
        Map<String, UnifiedResult> byKey = new LinkedHashMap<>();
        for (UnifiedResult result : results) {
            String key = result.type() + "|" + result.title();
            byKey.putIfAbsent(key, result);
        }
        return new ArrayList<>(byKey.values());
    }

    /**
     * Hash embeddings are deliberately cheap; blending in a lexical signal
     * keeps Chinese keyword queries well ranked without hiding semantic hits.
     */
    private double blendedScore(String query, double vectorScore, DocumentRecord doc) {
        if (query == null || query.isBlank()) {
            return round(vectorScore);
        }
        String haystack = (doc.title() + " " + doc.summary() + " " + doc.content())
                .toLowerCase(Locale.ROOT);
        String compact = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        int total = 0;
        int matched = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i + 1 < compact.length(); i++) {
            String bigram = compact.substring(i, i + 2);
            if (seen.add(bigram)) {
                total++;
                if (haystack.contains(bigram)) {
                    matched++;
                }
            }
        }
        double lexical = total == 0 ? 0 : matched / (double) total;
        double titleBoost = doc.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                ? 0.15 : 0;
        double blended = 0.55 * Math.max(0, vectorScore) + 0.45 * Math.min(1, lexical + titleBoost);
        return round(Math.min(1.0, blended));
    }

    private int typeOrder(UnifiedResult.Kind kind) {
        return switch (kind) {
            case ACTION -> 0;
            case ENTITY -> 1;
            case KNOWLEDGE -> 2;
        };
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public record SearchResult(List<UnifiedResult> results, int trimmed, boolean imageQuery,
                               String embeddingProvider, long vectorChunks, long documents,
                               Consistency consistency) {
    }

    public record Consistency(long documents, long vectorChunks, double deviation,
                              boolean alert, String message) {
    }
}
