-- Super-admin governance: explicit manage permission + permission requests.
INSERT INTO sys_resource (id, code, name, type, parent_code, sort_order, icon, description) VALUES
 (23, 'rbac:manage', '权限中心管理', 'API', 'menu:system', 25, 'key', '编辑角色授权、数据范围与菜单资源');

INSERT INTO sys_role_resource (role_id, resource_id)
SELECT 1, 23 WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_resource WHERE role_id = 1 AND resource_id = 23
);

CREATE TABLE permission_request (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    requester_user_id BIGINT NOT NULL,
    requester_username VARCHAR(64) NOT NULL,
    requester_name VARCHAR(64) NOT NULL,
    role_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    decided_by VARCHAR(64) NULL,
    decided_at TIMESTAMP NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_permission_request_status ON permission_request(status);
CREATE INDEX idx_permission_request_user ON permission_request(requester_user_id);
