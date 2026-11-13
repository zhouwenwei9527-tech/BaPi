-- The demo admin can act as any seed role so the shell can demonstrate live
-- RBAC switching (menu / desktop / palette all share one guard).
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 2), (1, 3), (1, 4);
