package io.aurora.rbac.repo;

import io.aurora.rbac.domain.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);

    List<SysUser> findByDeptIdIn(Collection<Long> deptIds);

    List<SysUser> findByStatusOrderByIdAsc(String status);

    @Query("""
            select u from SysUser u
            where (:allScope = true or u.id = :selfId or u.deptId in :deptIds)
              and (:includeDisabled = true or u.status = 'ACTIVE')
              and (:q = '' or lower(u.displayName) like lower(concat('%', :q, '%'))
                   or lower(u.username) like lower(concat('%', :q, '%'))
                   or lower(u.title) like lower(concat('%', :q, '%')))
            """)
    Page<SysUser> searchScoped(boolean allScope, Long selfId, Collection<Long> deptIds,
                               boolean includeDisabled, String q, Pageable pageable);
}
