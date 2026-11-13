package io.aurora.agent.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentSessionRepository extends JpaRepository<AgentSessionEntity, String> {
    List<AgentSessionEntity> findAllByUserIdOrderByUpdatedAtDesc(long userId);

    Optional<AgentSessionEntity> findByIdAndUserId(String id, long userId);
}
