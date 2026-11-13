package io.aurora.rbac.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sys_session")
public class SysSession {

    @Id
    @Column(length = 36)
    public String id;

    @Column(nullable = false, length = 64)
    public String tokenHash;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Long activeRoleId;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant expiresAt;

    @Column(nullable = false)
    public Instant lastSeenAt;

    public SysSession() {
    }
}
