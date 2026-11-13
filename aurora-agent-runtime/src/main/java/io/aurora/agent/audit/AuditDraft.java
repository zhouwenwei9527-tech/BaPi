package io.aurora.agent.audit;

import io.aurora.contract.Actor;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable draft of an audit event. The service computes prevHash/hash.
 */
public final class AuditDraft {

    private String eventId = UUID.randomUUID().toString();
    private Instant ts = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private String traceId;
    private String sessionId;
    private String type;
    private Actor actor = Actor.user();
    private Long userId;
    private String username;
    private List<String> roles = List.of();
    private String toolName;
    private String argsDigest;
    private String decision;
    private String approvalId;
    private Long latencyMs;
    private String status = "OK";
    private String refsJson = "[]";

    private AuditDraft(String type) {
        this.type = type;
    }

    public static AuditDraft of(String type) {
        return new AuditDraft(type);
    }

    public AuditDraft trace(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public AuditDraft session(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public AuditDraft actor(Actor actor) {
        this.actor = actor;
        return this;
    }

    public AuditDraft onBehalfOf(Principal principal) {
        if (principal != null) {
            this.userId = principal.userId();
            this.username = principal.displayName();
            this.roles = List.copyOf(principal.roles());
        }
        return this;
    }

    public AuditDraft tool(String toolName) {
        this.toolName = toolName;
        return this;
    }

    public AuditDraft argsDigest(String argsDigest) {
        this.argsDigest = argsDigest;
        return this;
    }

    public AuditDraft decision(String decision) {
        this.decision = decision;
        return this;
    }

    public AuditDraft approval(String approvalId) {
        this.approvalId = approvalId;
        return this;
    }

    public AuditDraft latency(long latencyMs) {
        this.latencyMs = latencyMs;
        return this;
    }

    public AuditDraft status(String status) {
        this.status = status;
        return this;
    }

    public AuditDraft identity(Long userId, String username, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        return this;
    }

    public AuditDraft event(String eventId, Instant ts, String refsJson) {
        this.eventId = eventId;
        this.ts = ts;
        this.refsJson = refsJson;
        return this;
    }

    public AuditDraft refs(List<Map<String, String>> refs) {
        this.refsJson = Json.write(refs == null ? List.of() : refs);
        return this;
    }

    public String eventId() {
        return eventId;
    }

    Map<String, Object> canonicalPayload() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", eventId);
        map.put("ts", ts.toString());
        map.put("traceId", traceId);
        map.put("sessionId", sessionId);
        map.put("type", type);
        map.put("actorType", actor == null ? null : actor.type().name());
        map.put("agentId", actor == null ? null : actor.agentId());
        map.put("userId", userId);
        map.put("username", username);
        map.put("roles", roles == null ? List.of() : new ArrayList<>(roles));
        map.put("toolName", toolName);
        map.put("argsDigest", argsDigest);
        map.put("decision", decision);
        map.put("approvalId", approvalId);
        map.put("latencyMs", latencyMs);
        map.put("status", status);
        map.put("refsJson", refsJson);
        return map;
    }

    public String canonicalJson() {
        return Json.canonical(Json.mapper().valueToTree(canonicalPayload()));
    }

    public Instant ts() {
        return ts;
    }

    public String type() {
        return type;
    }

    public String traceId() {
        return traceId;
    }

    public String sessionId() {
        return sessionId;
    }

    public Actor actor() {
        return actor;
    }

    public Long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public List<String> roles() {
        return roles;
    }

    public String toolName() {
        return toolName;
    }

    public String argsDigest() {
        return argsDigest;
    }

    public String decision() {
        return decision;
    }

    public String approvalId() {
        return approvalId;
    }

    public Long latencyMs() {
        return latencyMs;
    }

    public String status() {
        return status;
    }

    public String refsJson() {
        return refsJson;
    }
}
