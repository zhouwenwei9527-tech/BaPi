package io.aurora.rbac.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String username;

    @Column(nullable = false, length = 64)
    public String displayName;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    public String passwordHash;

    @Column(nullable = false)
    public Long deptId;

    @Column(nullable = false, length = 20)
    public String status = "ACTIVE";

    @Column(nullable = false, length = 64)
    public String title = "";

    @Column(nullable = false)
    public int avatarHue = 210;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    public SysUser() {
    }

    public SysUser(String username, String displayName, String passwordHash, Long deptId) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.deptId = deptId;
    }
}
