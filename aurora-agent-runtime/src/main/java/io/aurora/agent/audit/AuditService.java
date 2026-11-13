package io.aurora.agent.audit;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only audit log with a SHA-256 hash chain (INV-6, AC-16).
 *
 * The application account is granted INSERT/SELECT only on audit_event by
 * docker/mysql-init; this service has no update/delete operations at all.
 */
@Service
public class AuditService {

    public static final String TYPE_RETRIEVAL = "RETRIEVAL";
    public static final String TYPE_TOOL_CALL = "TOOL_CALL";
    public static final String TYPE_PERMISSION = "PERMISSION";
    public static final String TYPE_APPROVAL = "APPROVAL";
    public static final String TYPE_MODEL_CALL = "MODEL_CALL";
    public static final String TYPE_BUDGET = "BUDGET";
    public static final String TYPE_PLUGIN = "PLUGIN";

    private static final String CHAIN_LOCK = "aurora_audit_chain";
    private static final ReentrantLock JVM_LOCK = new ReentrantLock(true);

    private final JdbcTemplate jdbc;
    private final AtomicInteger forcedFailures = new AtomicInteger(0);

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Test hook for AC-09: simulate audit storage becoming unavailable. */
    public void simulateUnavailableForNextWrites(int count) {
        forcedFailures.set(count);
    }

    public AuditRecord record(AuditDraft draft) {
        if (forcedFailures.getAndUpdate(v -> Math.max(0, v - 1)) > 0) {
            throw new AuroraException(AuroraErrorCode.AUDIT_UNAVAILABLE, "审计存储不可用（模拟）");
        }
        try {
            return jdbc.execute((ConnectionCallback<AuditRecord>) connection -> appendInLock(connection, draft));
        } catch (AuroraException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new AuroraException(AuroraErrorCode.AUDIT_UNAVAILABLE, "审计写入失败，操作已中止", e);
        } catch (Exception e) {
            throw new AuroraException(AuroraErrorCode.AUDIT_UNAVAILABLE, "审计写入失败，操作已中止", e);
        }
    }

    private AuditRecord appendInLock(Connection connection, AuditDraft draft) throws SQLException {
        boolean mysql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        boolean locked = false;
        if (mysql) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT GET_LOCK(?, 5)")) {
                ps.setString(1, CHAIN_LOCK);
                try (var rs = ps.executeQuery()) {
                    locked = rs.next() && rs.getInt(1) == 1;
                }
            }
            if (!locked) {
                throw new AuroraException(AuroraErrorCode.AUDIT_UNAVAILABLE, "无法获取审计链锁");
            }
        } else {
            JVM_LOCK.lock();
        }
        try {
            String prevHash = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT hash FROM audit_event ORDER BY id DESC LIMIT 1");
                 var rs = ps.executeQuery()) {
                if (rs.next()) {
                    prevHash = rs.getString(1);
                }
            }
            String payload = draft.canonicalJson();
            String hash = sha256((prevHash == null ? "GENESIS" : prevHash) + "|" + payload);

            String sql = """
                    INSERT INTO audit_event (event_id, ts, trace_id, session_id, type, actor_type, agent_id,
                        user_id, username, roles, tool_name, args_digest, decision, approval_id, latency_ms,
                        status, refs_json, prev_hash, hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            long id;
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, draft.eventId());
                ps.setTimestamp(2, java.sql.Timestamp.from(draft.ts()));
                ps.setString(3, draft.traceId());
                ps.setString(4, draft.sessionId());
                ps.setString(5, draft.type());
                ps.setString(6, draft.actor() == null ? "USER" : draft.actor().type().name());
                ps.setString(7, draft.actor() == null ? null : draft.actor().agentId());
                if (draft.userId() == null) {
                    ps.setObject(8, null);
                } else {
                    ps.setLong(8, draft.userId());
                }
                ps.setString(9, draft.username());
                ps.setString(10, Json.write(draft.roles()));
                ps.setString(11, draft.toolName());
                ps.setString(12, draft.argsDigest());
                ps.setString(13, draft.decision());
                ps.setString(14, draft.approvalId());
                if (draft.latencyMs() == null) {
                    ps.setObject(15, null);
                } else {
                    ps.setLong(15, draft.latencyMs());
                }
                ps.setString(16, draft.status());
                ps.setString(17, draft.refsJson());
                ps.setString(18, prevHash);
                ps.setString(19, hash);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    throw new AuroraException(AuroraErrorCode.AUDIT_UNAVAILABLE, "审计写入未生效");
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    id = keys.next() ? keys.getLong(1) : -1;
                }
            }
            return new AuditRecord(id, draft.eventId(), hash, prevHash);
        } finally {
            if (mysql && locked) {
                try (PreparedStatement ps = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    ps.setString(1, CHAIN_LOCK);
                    ps.executeQuery().close();
                } catch (Exception ignored) {
                    // Connection close releases the lock as a safety net.
                }
            } else {
                JVM_LOCK.unlock();
            }
        }
    }

    public List<AuditEventView> recent(int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        SqlRowSet rs = jdbc.queryForRowSet("""
                SELECT id, event_id, ts, trace_id, session_id, type, actor_type, agent_id, user_id,
                       username, roles, tool_name, args_digest, decision, approval_id, latency_ms,
                       status, prev_hash, hash
                FROM audit_event ORDER BY id DESC LIMIT ?
                """, safe);
        List<AuditEventView> out = new ArrayList<>();
        while (rs.next()) {
            out.add(mapRow(rs));
        }
        return out;
    }

    public List<AuditEventView> byTrace(String traceId) {
        SqlRowSet rs = jdbc.queryForRowSet("""
                SELECT id, event_id, ts, trace_id, session_id, type, actor_type, agent_id, user_id,
                       username, roles, tool_name, args_digest, decision, approval_id, latency_ms,
                       status, prev_hash, hash
                FROM audit_event WHERE trace_id = ? ORDER BY id ASC
                """, traceId);
        return mapRows(rs);
    }

    public List<AuditEventView> byTool(String toolName, int limit) {
        SqlRowSet rs = jdbc.queryForRowSet("""
                SELECT id, event_id, ts, trace_id, session_id, type, actor_type, agent_id, user_id,
                       username, roles, tool_name, args_digest, decision, approval_id, latency_ms,
                       status, prev_hash, hash
                FROM audit_event WHERE tool_name = ? ORDER BY id DESC LIMIT ?
                """, toolName, limit);
        return mapRows(rs);
    }

    public long count() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class);
        return value == null ? 0 : value;
    }

    public ChainVerification verifyChain() {
        SqlRowSet rs = jdbc.queryForRowSet("""
                SELECT id, event_id, ts, trace_id, session_id, type, actor_type, agent_id, user_id,
                       username, roles, tool_name, args_digest, decision, approval_id, latency_ms,
                       status, prev_hash, hash, refs_json
                FROM audit_event ORDER BY id ASC
                """);
        String prev = null;
        long checked = 0;
        while (rs.next()) {
            checked++;
            long id = rs.getLong("id");
            String expectedPrev = prev;
            String hash = rs.getString("hash");
            AuditDraft replay = AuditDraft.of(rs.getString("type"))
                    .trace(rs.getString("trace_id"))
                    .session(rs.getString("session_id"))
                    .tool(rs.getString("tool_name"))
                    .argsDigest(rs.getString("args_digest"))
                    .decision(rs.getString("decision"))
                    .approval(rs.getString("approval_id"))
                    .status(rs.getString("status"));
            if (rs.getObject("latency_ms") != null) {
                replay.latency(rs.getLong("latency_ms"));
            }
            // Fill identity fields through the public builder surface.
            replay.actor(new io.aurora.contract.Actor(
                    io.aurora.contract.Actor.Type.valueOf(rs.getString("actor_type")),
                    rs.getString("agent_id")));
            Long replayUserId = rs.getObject("user_id") == null ? null : rs.getLong("user_id");
            replay.identity(replayUserId, rs.getString("username"), parseRoles(rs.getString("roles")));
            replay.event(rs.getString("event_id"), rs.getTimestamp("ts").toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                    rs.getString("refs_json"));
            String payload = replay.canonicalJson();
            String expected = sha256((expectedPrev == null ? "GENESIS" : expectedPrev) + "|" + payload);
            if (!expected.equals(hash)) {
                return new ChainVerification(false, checked, id,
                        "哈希链在事件 " + id + " 处断裂（记录可能被篡改）");
            }
            String storedPrev = rs.getString("prev_hash");
            if (!java.util.Objects.equals(storedPrev, expectedPrev)) {
                return new ChainVerification(false, checked, id, "prev_hash 不连续于事件 " + id);
            }
            prev = hash;
        }
        return new ChainVerification(true, checked, null, "哈希链完整，共校验 " + checked + " 条事件");
    }

    private List<AuditEventView> mapRows(SqlRowSet rs) {
        List<AuditEventView> out = new ArrayList<>();
        while (rs.next()) {
            out.add(mapRow(rs));
        }
        return out;
    }

    private AuditEventView mapRow(SqlRowSet rs) {
        return new AuditEventView(
                rs.getLong("id"), rs.getString("event_id"),
                rs.getTimestamp("ts").toInstant().toString(),
                rs.getString("trace_id"), rs.getString("session_id"), rs.getString("type"),
                rs.getString("actor_type"), rs.getString("agent_id"),
                rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                rs.getString("username"), parseRoles(rs.getString("roles")),
                rs.getString("tool_name"), rs.getString("args_digest"), rs.getString("decision"),
                rs.getString("approval_id"),
                rs.getObject("latency_ms") == null ? null : rs.getLong("latency_ms"),
                rs.getString("status"), rs.getString("prev_hash"), rs.getString("hash"));
    }

    private List<String> parseRoles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return List.of();
        }
        try {
            return Json.mapper().readValue(rolesJson,
                    Json.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record AuditRecord(long id, String eventId, String hash, String prevHash) {
    }

    public record AuditEventView(long seq, String eventId, String ts, String traceId, String sessionId,
                                 String type, String actorType, String agentId, Long userId,
                                 String username, List<String> roles, String toolName, String argsDigest,
                                 String decision, String approvalId, Long latencyMs, String status,
                                 String prevHash, String hash) {
    }

    public record ChainVerification(boolean valid, long checked, Long brokenAt, String message) {
    }
}
