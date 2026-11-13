package io.aurora.rbac.repo;

import io.aurora.rbac.domain.SysSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysSessionRepository extends JpaRepository<SysSession, String> {
    Optional<SysSession> findByTokenHash(String tokenHash);
}
