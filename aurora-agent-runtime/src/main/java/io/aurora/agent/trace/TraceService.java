package io.aurora.agent.trace;

import io.aurora.contract.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Append-only trajectory events used for deterministic replay (PRD §8, AC-14).
 * Replay never calls the model again.
 */
@Service
public class TraceService {

    private final JdbcTemplate jdbc;

    public TraceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long append(String traceId, String stage, String status, String decision, Object detail) {
        String detailJson = detail == null ? "{}" : Json.write(detail);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO trace_event (trace_id, ts, stage, status, decision, detail_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, traceId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, stage);
            ps.setString(4, status);
            ps.setString(5, decision == null ? null
                    : decision.substring(0, Math.min(200, decision.length())));
            ps.setString(6, detailJson);
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? -1 : key.longValue();
    }

    public List<TraceStep> timeline(String traceId) {
        List<TraceStep> steps = new ArrayList<>();
        jdbc.query("""
                SELECT id, trace_id, ts, stage, status, decision, detail_json
                FROM trace_event WHERE trace_id = ? ORDER BY id ASC
                """, rs -> {
            steps.add(new TraceStep(rs.getLong("id"), rs.getString("trace_id"),
                    rs.getTimestamp("ts").toInstant().toString(), rs.getString("stage"),
                    rs.getString("status"), rs.getString("decision"),
                    readJson(rs.getString("detail_json"))));
        }, traceId);
        return steps;
    }

    public List<TraceSummary> recentTraces(int limit) {
        int safe = Math.max(1, Math.min(limit, 100));
        List<TraceSummary> result = new ArrayList<>();
        jdbc.query("""
                SELECT trace_id, MIN(ts) AS started, MAX(ts) AS ended, COUNT(*) AS steps,
                       MIN(detail_json) AS detail
                FROM trace_event GROUP BY trace_id ORDER BY started DESC LIMIT ?
                """, rs -> {
            result.add(new TraceSummary(rs.getString("trace_id"),
                    rs.getTimestamp("started").toInstant().toString(),
                    rs.getTimestamp("ended").toInstant().toString(),
                    rs.getLong("steps"), rs.getString("detail")));
        }, safe);
        return result;
    }

    private Object readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return Json.mapper().readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    public record TraceStep(long seq, String traceId, String ts, String stage, String status,
                            String decision, Object detail) {
    }

    public record TraceSummary(String traceId, String startedAt, String endedAt, long steps,
                               String sampleDetail) {
    }
}
