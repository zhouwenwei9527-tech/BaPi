package io.aurora.rbac.repo;

import io.aurora.rbac.domain.SysRoleResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SysRoleResourceRepository extends JpaRepository<SysRoleResource, Long> {
    List<SysRoleResource> findByRoleId(Long roleId);

    List<SysRoleResource> findByRoleIdIn(Collection<Long> roleIds);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from SysRoleResource rr where rr.roleId = :roleId")
    int deleteAllByRoleId(Long roleId);
}
