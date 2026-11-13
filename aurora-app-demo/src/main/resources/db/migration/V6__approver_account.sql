-- An independent approver so the demo can exercise separation of duties:
-- admin/ops create a WRITE request, auditor reviews and executes it.
INSERT INTO sys_role (id, code, name, description) VALUES
 (5, 'security', '安全审计员', '独立审批人，全量权限，用于职责分离演示');

INSERT INTO sys_data_scope_rule (role_id, type, custom_dept_ids) VALUES
 (5, 'ALL', '');

INSERT INTO sys_role_resource (role_id, resource_id)
SELECT 5, id FROM sys_resource;

INSERT INTO sys_user (id, username, display_name, password_hash, dept_id, status, title, avatar_hue) VALUES
 (10, 'auditor', '周衡', '$2b$12$OpTVq4Uh4MiM3ocR/5Q19.MIqEibgRSjISNPgV9nG3rQI8YgpfPCe', 1, 'ACTIVE', '安全审计员', 15);

INSERT INTO sys_user_role (user_id, role_id) VALUES (10, 5);
