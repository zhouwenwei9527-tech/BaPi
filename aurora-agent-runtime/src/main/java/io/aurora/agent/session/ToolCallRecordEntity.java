package io.aurora.agent.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tool_call_record")
public class ToolCallRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 36)
    public String sessionId;

    @Column(nullable = false)
    public long userId;

    @Column(nullable = false, length = 64)
    public String traceId;

    @Column(nullable = false, length = 100)
    public String toolName;

    @Column(nullable = false, length = 64)
    public String argsDigest;

    @Column(nullable = false, length = 32)
    public String decision;

    @Column(length = 36)
    public String approvalId;

    @Column(length = 500)
    public String summary = "";

    @Column(nullable = false, length = 16)
    public String status;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public ToolCallRecordEntity() {
    }
}
