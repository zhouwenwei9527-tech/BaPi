package io.aurora.rbac.repo;

import io.aurora.rbac.domain.SysResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysResourceRepository extends JpaRepository<SysResource, Long> {
    Optional<SysResource> findByCode(String code);

    List<SysResource> findByIdIn(Collection<Long> ids);

    List<SysResource> findAllByOrderBySortOrderAsc();

    List<SysResource> findByTypeOrderBySortOrderAsc(String type);
}
