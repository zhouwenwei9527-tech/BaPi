package io.aurora.agent.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {

    List<ApprovalEntity> findAllByOrderByCreatedAtDesc();

    List<ApprovalEntity> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    long countByStatus(ApprovalStatus status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update ApprovalEntity a set a.status = :to,
                   a.decidedBy = :by, a.decidedAt = :at, a.note = :note
            where a.id = :id and a.status = :from
            """)
    int claimDecision(String id, ApprovalStatus from, ApprovalStatus to, String by,
                      Instant at, String note);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update ApprovalEntity a set a.status = :to,
                   a.decidedBy = 'system', a.decidedAt = :at
            where a.id = :id and a.status = :from
            """)
    int claimExpiry(String id, ApprovalStatus from, ApprovalStatus to, Instant at);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update ApprovalEntity a set a.status = :to,
                   a.executedAt = :at, a.resultJson = :result, a.errorCode = null
            where a.id = :id and a.status = :from
            """)
    int markExecution(String id, ApprovalStatus from, ApprovalStatus to, Instant at, String result);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update ApprovalEntity a set a.status = :to,
                   a.executedAt = :at, a.errorCode = :error
            where a.id = :id and a.status = :from
            """)
    int markFailure(String id, ApprovalStatus from, ApprovalStatus to, Instant at, String error);
}
