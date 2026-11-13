package io.aurora.rbac.repo;

import io.aurora.rbac.domain.SysDataScopeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysDataScopeRuleRepository extends JpaRepository<SysDataScopeRule, Long> {
    List<SysDataScopeRule> findByRoleId(Long roleId);

    List<SysDataScopeRule> findByRoleIdIn(List<Long> roleIds);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from SysDataScopeRule r where r.roleId = :roleId")
    int deleteAllByRoleId(Long roleId);
}
