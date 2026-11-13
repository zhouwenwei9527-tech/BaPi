package io.aurora.agent.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_session")
public class AgentSessionEntity {

    @Id
    @Column(length = 36)
    public String id;

    @Column(nullable = false)
    public long userId;

    @Column(nullable = false, length = 120)
    public String title;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    public AgentSessionEntity() {
    }
}
