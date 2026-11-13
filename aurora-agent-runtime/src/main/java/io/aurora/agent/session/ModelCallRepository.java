package io.aurora.agent.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelCallRepository extends JpaRepository<ModelCallEntity, Long> {
    List<ModelCallEntity> findByTraceIdOrderByIdAsc(String traceId);

    List<ModelCallEntity> findBySessionIdOrderByIdAsc(String sessionId);
}
