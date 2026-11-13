package io.aurora.rbac.repo;

import io.aurora.rbac.domain.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {
    List<SysUserRole> findByUserId(Long userId);

    List<SysUserRole> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Transactional
    @Modifying
    @Query("delete from SysUserRole ur where ur.userId = :userId and ur.roleId = :roleId")
    int deleteByUserIdAndRoleId(Long userId, Long roleId);

    @Transactional
    @Modifying
    @Query("delete from SysUserRole ur where ur.userId = :userId")
    int deleteAllByUserId(Long userId);
}
