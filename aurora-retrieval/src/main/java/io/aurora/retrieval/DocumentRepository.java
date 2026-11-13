package io.aurora.retrieval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DocumentRecord> findAll() {
        return jdbc.query("SELECT * FROM kb_document ORDER BY created_at ASC", this::map);
    }

    public Optional<DocumentRecord> findById(String id) {
        List<DocumentRecord> rows = jdbc.query("SELECT * FROM kb_document WHERE id = ?", this::map, id);
        return rows.stream().findFirst();
    }

    public void insert(DocumentRecord doc) {
        jdbc.update("""
                INSERT INTO kb_document (id, title, summary, content, object_type, object_id,
                    owner_id, visibility, visible_roles, visible_depts, acl_version, page_no,
                    para_no, required_permission, storage_key, mime_type, file_name, file_size,
                    created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, doc.id(), doc.title(), doc.summary(), doc.content(), doc.objectType(),
                doc.objectId(), doc.ownerId(), doc.visibility(),
                String.join(",", doc.visibleRoles()),
                doc.visibleDepts().stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",")),
                doc.aclVersion(), doc.page(), doc.paragraph(), doc.requiredPermission(),
                doc.storageKey(), doc.mimeType(), doc.fileName(), doc.fileSize(),
                java.sql.Timestamp.from(doc.createdAt()));
    }

    public long count() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM kb_document", Long.class);
        return value == null ? 0 : value;
    }

    public void updateAcl(String id, String visibility, String rolesCsv, String deptsCsv,
                          String requiredPermission) {
        jdbc.update("""
                UPDATE kb_document
                SET visibility = ?, visible_roles = ?, visible_depts = ?,
                    required_permission = ?, acl_version = acl_version + 1
                WHERE id = ?
                """, visibility, rolesCsv, deptsCsv, requiredPermission, id);
    }

    public int aclVersion(String id) {
        Integer value = jdbc.queryForObject("SELECT acl_version FROM kb_document WHERE id = ?",
                Integer.class, id);
        return value == null ? 0 : value;
    }

    private DocumentRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentRecord(
                rs.getString("id"), rs.getString("title"), rs.getString("summary"),
                rs.getString("content"), rs.getString("object_type"), rs.getString("object_id"),
                rs.getLong("owner_id"), rs.getString("visibility"),
                csv(rs.getString("visible_roles")), deptCsv(rs.getString("visible_depts")),
                rs.getInt("acl_version"), rs.getInt("page_no"), rs.getInt("para_no"),
                rs.getString("required_permission"), rs.getString("storage_key"),
                rs.getString("mime_type"), rs.getString("file_name"), rs.getLong("file_size"),
                rs.getTimestamp("created_at").toInstant());
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
                // Ignore malformed ids.
            }
        }
        return set;
    }
}
