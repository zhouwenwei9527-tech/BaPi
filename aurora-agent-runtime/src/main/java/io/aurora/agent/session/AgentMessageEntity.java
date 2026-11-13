package io.aurora.agent.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_message")
public class AgentMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 36)
    public String sessionId;

    /** user / assistant / system / tool */
    @Column(nullable = false, length = 16)
    public String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String content = "";

    @Column(columnDefinition = "TEXT")
    public String toolCallsJson;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public AgentMessageEntity() {
    }
}
