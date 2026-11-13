package io.aurora.agent.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolCallRecordRepository extends JpaRepository<ToolCallRecordEntity, Long> {
    List<ToolCallRecordEntity> findTop20ByUserIdOrderByIdDesc(long userId);

    List<ToolCallRecordEntity> findBySessionIdOrderByIdAsc(String sessionId);
}
