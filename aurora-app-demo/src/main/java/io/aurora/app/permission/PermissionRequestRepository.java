package io.aurora.app.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface PermissionRequestRepository extends JpaRepository<PermissionRequestEntity, String> {

    List<PermissionRequestEntity> findAllByOrderByCreatedAtDesc();

    List<PermissionRequestEntity> findByRequesterUserIdOrderByCreatedAtDesc(long requesterUserId);

    boolean existsByRequesterUserIdAndRoleCodeAndStatus(long requesterUserId, String roleCode,
                                                        String status);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PermissionRequestEntity r set r.status = :to, r.decidedBy = :by,
                   r.decidedAt = :at, r.note = :note
            where r.id = :id and r.status = :from
            """)
    int claimDecision(String id, String from, String to, String by, Instant at, String note);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PermissionRequestEntity r set r.status = :to, r.decidedBy = :by,
                   r.decidedAt = :at, r.note = :note
            where r.id = :id and r.status = :from
            """)
    int markFailed(String id, String from, String to, String by, Instant at, String note);
}
