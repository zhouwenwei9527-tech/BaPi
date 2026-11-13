package io.aurora.agent.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "approval_request")
public class ApprovalEntity {

    @Id
    @Column(length = 36)
    public String id;

    @Column(nullable = false, length = 100)
    public String toolName;

    @Column(nullable = false, length = 24)
    public String toolVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String argsJson;

    @Column(nullable = false, length = 64)
    public String argsHash;

    @Column(nullable = false, length = 500)
    public String argsSummary;

    @Column(nullable = false, length = 500)
    public String intentSummary = "";

    @Column(nullable = false)
    public int operability = 80;

    @Column(nullable = false, length = 16)
    public String riskLevel = "MEDIUM";

    @Column(nullable = false, length = 500)
    public String riskReasons = "";

    @Column(nullable = false)
    public boolean requiresConfirmation = false;

    @Column(length = 500)
    public String affectedObjects;

    @Column(nullable = false)
    public int affectedCount;

    @Column(nullable = false, length = 16)
    public String sideEffect;

    @Column(nullable = false, length = 64)
    public String reversible;

    @Column(nullable = false)
    public long requesterUserId;

    @Column(nullable = false, length = 64)
    public String requesterName;

    @Column(nullable = false)
    public long requesterRoleId;

    @Column(nullable = false, length = 16)
    public String actorType;

    @Column(length = 64)
    public String agentId;

    @Column(length = 64)
    public String sessionId;

    @Column(length = 64)
    public String traceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(length = 64)
    public String decidedBy;

    public Instant decidedAt;

    public Instant executedAt;

    @Column(columnDefinition = "TEXT")
    public String resultJson;

    @Column(length = 48)
    public String errorCode;

    @Column(length = 500)
    public String note;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant expiresAt;

    @Version
    public long version;

    public ApprovalEntity() {
    }
}
