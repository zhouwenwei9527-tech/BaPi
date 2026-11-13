package io.aurora.agent.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMemoryRepository extends JpaRepository<AgentMemoryEntity, Long> {
    List<AgentMemoryEntity> findByUserIdOrderByIdDesc(long userId);

    long deleteByIdAndUserId(long id, long userId);
}
