package io.aurora.app.acceptance;

import io.aurora.contract.UnifiedResult;
import io.aurora.retrieval.DocumentRepository;
import io.aurora.retrieval.EmbeddingPort;
import io.aurora.retrieval.InMemoryVectorStore;
import io.aurora.retrieval.RetrievalService;
import io.aurora.retrieval.VectorQuery;
import io.aurora.retrieval.VectorRecord;
import io.aurora.retrieval.VectorStorePort;
import io.aurora.rbac.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalAcceptanceTest extends BaseIntegrationTest {

    @Autowired RetrievalService retrievalService;
    @Autowired DocumentRepository documentRepository;
    @Autowired VectorStorePort vectorStore;
    @Autowired EmbeddingPort embedding;

    @Test
    @DisplayName("AC-10 无权访问的知识切片不进入结果；ACL 过滤在向量查询内部")
    void aclFiltersBeforeRecall() {
        List<UnifiedResult> financeResults = retrievalService.search(finance(),
                "薪酬分级", null, null, null, 10).results();
        assertThat(financeResults)
                .anyMatch(r -> "岗位薪酬分级办法".equals(r.title()));

        List<UnifiedResult> opsResults = retrievalService.search(ops(),
                "薪酬分级", null, null, null, 10).results();
        assertThat(opsResults)
                .noneMatch(r -> "岗位薪酬分级办法".equals(r.title()));

        var direct = new InMemoryVectorStore();
        VectorRecord restricted = new VectorRecord("v1", 0, "doc", "d3", "d3", "HR",
                "薪酬", 1, 1, 3, "ROLES", Set.of("finance", "admin"), Set.of(),
                1, "knowledge:hr", "hr.pdf", embedding.embedText("薪酬"));
        direct.upsert(restricted);
        assertThat(direct.search(new VectorQuery(0, embedding.embedText("薪酬"), 5, ops()))).isEmpty();
        assertThat(direct.search(new VectorQuery(0, embedding.embedText("薪酬"), 5, finance())))
                .hasSize(1);
    }

    @Test
    @DisplayName("AC-11 ACL 变更窗口内，MySQL 当前 ACL 二次校验立即生效")
    void aclChangeIsImmediateEvenWithStalePayload() {
        int before = retrievalService.search(ops(), "数据范围", null, null, null, 10).results().stream()
                .filter(r -> "d1".equals(r.sourceRef() == null ? null : r.sourceRef().objectId()))
                .toList().size();
        assertThat(before).isPositive();

        documentRepository.updateAcl("d1", "ROLES", "finance,admin", "", "knowledge:internal");
        try {
            RetrievalService.SearchResult stale = retrievalService.search(ops(), "数据范围",
                    null, null, null, 10);
            assertThat(stale.results())
                    .noneMatch(r -> r.sourceRef() != null && "d1".equals(r.sourceRef().objectId()));
            assertThat(stale.trimmed()).isPositive();
        } finally {
            documentRepository.updateAcl("d1", "PUBLIC", "", "", "knowledge:public");
            vectorIndexer.reindexAll();
        }
    }

    @Test
    @DisplayName("AC-12 图片 + 文字查询返回带 sourceRef 的结果")
    void imageQueryReturnsSourceRefs() {
        byte[] image = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        RetrievalService.SearchResult result = retrievalService.search(admin(), "薪酬",
                image, "薪酬制度 财务", "salary.png", 8);
        assertThat(result.imageQuery()).isTrue();
        assertThat(result.results())
                .anyMatch(r -> r.type() == UnifiedResult.Kind.KNOWLEDGE
                        && r.sourceRef() != null && r.sourceRef().page() != null);
    }

    @Test
    @DisplayName("AC-13 MySQL 与向量库计数偏差超过阈值时告警")
    void consistencyAlertsOnDeviation() {
        vectorIndexer.reindexAll();
        RetrievalService.Consistency healthy = retrievalService.consistency();
        assertThat(healthy.alert()).isFalse();
        vectorStore.deleteByDoc("d3");
        try {
            RetrievalService.Consistency broken = retrievalService.consistency();
            assertThat(broken.alert()).isTrue();
            assertThat(broken.deviation()).isGreaterThan(0.01);
        } finally {
            vectorIndexer.reindexAll();
        }
    }
}
