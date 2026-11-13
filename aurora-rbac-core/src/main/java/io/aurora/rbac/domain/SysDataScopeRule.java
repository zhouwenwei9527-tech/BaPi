package io.aurora.rbac.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_data_scope_rule")
public class SysDataScopeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long roleId;

    /** ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM */
    @Column(nullable = false, length = 24)
    public String type;

    /** Comma separated department ids for CUSTOM. */
    @Column(length = 500)
    public String customDeptIds = "";

    public SysDataScopeRule() {
    }
}
