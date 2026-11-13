package io.aurora.rbac.repo;

import io.aurora.rbac.domain.SysDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDepartmentRepository extends JpaRepository<SysDepartment, Long> {
}
