package io.aurora.agent.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_memory")
public class AgentMemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public long userId;

    @Column(nullable = false, length = 120)
    public String memoryKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String memoryValue;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public AgentMemoryEntity() {
    }
}
