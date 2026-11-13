package io.aurora.retrieval;

import io.aurora.rbac.Principal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * pgvector adapter. The WHERE clause embeds the ACL predicate so recall is
 * permission-filtered at the database (AC-10). The exact same predicate is
 * mirrored in InMemoryVectorStore for tests.
 */
@Component
@ConditionalOnProperty(name = "aurora.retrieval.store", havingValue = "pgvector")
public class PgVectorStore implements VectorStorePort {

    private final JdbcTemplate pg;

    public PgVectorStore(@Qualifier("vectorJdbcTemplate") JdbcTemplate pg,
                         @Qualifier("vectorDataSource") DataSource dataSource) {
        this.pg = pg;
        pg.execute("CREATE EXTENSION IF NOT EXISTS vector");
        pg.execute("""
                CREATE TABLE IF NOT EXISTS vector_chunk (
                    id VARCHAR(80) PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    object_type VARCHAR(40) NOT NULL,
                    object_id VARCHAR(80) NOT NULL,
                    doc_id VARCHAR(80) NOT NULL,
                    title VARCHAR(300) NOT NULL,
                    summary TEXT NOT NULL,
                    page_no INT NOT NULL DEFAULT 1,
                    para_no INT NOT NULL DEFAULT 1,
                    owner_id BIGINT NOT NULL,
                    visibility VARCHAR(16) NOT NULL,
                    visible_roles TEXT NOT NULL DEFAULT '',
                    visible_depts TEXT NOT NULL DEFAULT '',
                    acl_version INT NOT NULL DEFAULT 1,
                    required_permission VARCHAR(100),
                    file_name VARCHAR(300),
                    embedding vector(256) NOT NULL
                )
                """);
        pg.execute("CREATE INDEX IF NOT EXISTS idx_vector_chunk_doc ON vector_chunk(doc_id)");
    }

    @Override
    public List<VectorHit> search(VectorQuery query) {
        Principal principal = query.principal();
        boolean allDepts = principal.dataScope().isAll();
        String roles = principal.roles().isEmpty() ? "__none__" : String.join(",", principal.roles());
        String depts = principal.dataScope().deptIds().isEmpty()
                ? "__none__" : String.join(",", principal.dataScope().deptIds().stream()
                        .map(String::valueOf).toList());
        String sql = """
                SELECT id, tenant_id, object_type, object_id, doc_id, title, summary,
                       page_no, para_no, owner_id, visibility, visible_roles, visible_depts,
                       acl_version, required_permission, file_name,
                       1 - (embedding <=> ?::vector) AS score
                FROM vector_chunk
                WHERE tenant_id = ?
                  AND (
                    visibility = 'PUBLIC'
                    OR (visibility = 'ROLES' AND string_to_array(visible_roles, ',') && string_to_array(?, ','))
                    OR (visibility = 'DEPTS' AND ? = TRUE)
                    OR (visibility = 'DEPTS' AND string_to_array(visible_depts, ',') && string_to_array(?, ','))
                    OR (visibility = 'OWNER' AND owner_id = ?)
                  )
                  AND (required_permission IS NULL OR required_permission = ''
                       OR required_permission = ANY (string_to_array(?, ',')))
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        List<VectorHit> hits = new ArrayList<>();
        pg.query(sql, rs -> {
            VectorRecord record = new VectorRecord(
                    rs.getString("id"), rs.getLong("tenant_id"), rs.getString("object_type"),
                    rs.getString("object_id"), rs.getString("doc_id"), rs.getString("title"),
                    rs.getString("summary"), rs.getInt("page_no"), rs.getInt("para_no"),
                    rs.getLong("owner_id"), rs.getString("visibility"),
                    csv(rs.getString("visible_roles")), deptCsv(rs.getString("visible_depts")),
                    rs.getInt("acl_version"), rs.getString("required_permission"),
                    rs.getString("file_name"), new float[0]);
            hits.add(new VectorHit(rs.getDouble("score"), record));
        }, vectorLiteral(query.embedding()), query.tenantId(), roles, allDepts, depts,
                principal.userId(), permissionsCsv(principal), vectorLiteral(query.embedding()),
                Math.max(1, query.limit()));
        return hits;
    }

    @Override
    public void upsert(VectorRecord record) {
        pg.update("""
                INSERT INTO vector_chunk (id, tenant_id, object_type, object_id, doc_id, title, summary,
                    page_no, para_no, owner_id, visibility, visible_roles, visible_depts, acl_version,
                    required_permission, file_name, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title, summary = EXCLUDED.summary, page_no = EXCLUDED.page_no,
                    para_no = EXCLUDED.para_no, owner_id = EXCLUDED.owner_id,
                    visibility = EXCLUDED.visibility, visible_roles = EXCLUDED.visible_roles,
                    visible_depts = EXCLUDED.visible_depts, acl_version = EXCLUDED.acl_version,
                    required_permission = EXCLUDED.required_permission,
                    file_name = EXCLUDED.file_name, embedding = EXCLUDED.embedding
                """, record.id(), record.tenantId(), record.objectType(), record.objectId(),
                record.docId(), record.title(), record.summary(), record.page(), record.paragraph(),
                record.ownerId(), record.visibility(), String.join(",", record.visibleRoles()),
                record.visibleDepts().stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",")),
                record.aclVersion(), record.requiredPermission(), record.fileName(),
                vectorLiteral(record.embedding()));
    }

    @Override
    public void deleteByDoc(String docId) {
        pg.update("DELETE FROM vector_chunk WHERE doc_id = ?", docId);
    }

    @Override
    public long count() {
        Long value = pg.queryForObject("SELECT COUNT(*) FROM vector_chunk", Long.class);
        return value == null ? 0 : value;
    }

    private String permissionsCsv(Principal principal) {
        return principal.permissions().isEmpty() ? "__none__" : String.join(",", principal.permissions());
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).forEach(set::add);
        return set;
    }

    private Set<Long> deptCsv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<Long> set = new HashSet<>();
        for (String part : value.split(",")) {
            try {
                set.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Skip malformed ids.
            }
        }
        return set;
    }
}
