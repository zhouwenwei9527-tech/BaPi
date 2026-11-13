package io.aurora.rbac.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_resource")
public class SysResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 100)
    public String code;

    @Column(nullable = false, length = 64)
    public String name;

    /** MENU / API / TOOL / DATA */
    @Column(nullable = false, length = 16)
    public String type;

    @Column(length = 100)
    public String parentCode;

    @Column(nullable = false)
    public int sortOrder;

    @Column(nullable = false, length = 32)
    public String icon = "square";

    @Column(nullable = false, length = 200)
    public String description = "";

    public SysResource() {
    }
}
