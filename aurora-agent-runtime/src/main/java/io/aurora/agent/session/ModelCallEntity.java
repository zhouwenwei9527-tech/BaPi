package io.aurora.agent.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "model_call_record")
public class ModelCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 36)
    public String sessionId;

    @Column(nullable = false, length = 64)
    public String traceId;

    @Column(nullable = false)
    public int seq;

    @Column(nullable = false, length = 64)
    public String provider;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String requestJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String responseJson;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public ModelCallEntity() {
    }
}
