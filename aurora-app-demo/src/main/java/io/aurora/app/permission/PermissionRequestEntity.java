package io.aurora.app.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "permission_request")
public class PermissionRequestEntity {

    @Id
    @Column(length = 36)
    public String id;

    @Column(nullable = false)
    public long requesterUserId;

    @Column(nullable = false, length = 64)
    public String requesterUsername;

    @Column(nullable = false, length = 64)
    public String requesterName;

    @Column(nullable = false)
    public long roleId;

    @Column(nullable = false, length = 64)
    public String roleCode;

    @Column(nullable = false, length = 64)
    public String roleName;

    @Column(nullable = false, length = 500)
    public String reason = "";

    @Column(nullable = false, length = 16)
    public String status = "PENDING";

    @Column(length = 64)
    public String decidedBy;

    public Instant decidedAt;

    @Column(length = 500)
    public String note;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Version
    public long version;

    public PermissionRequestEntity() {
    }
}
