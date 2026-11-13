-- Optional production hardening, run as root AFTER the application has migrated.
-- The app currently connects as aurora_app. Switching the audit pool to
-- aurora_audit is the final step to enforce INSERT/SELECT only at the grant layer.
CREATE USER IF NOT EXISTS 'aurora_audit'@'%' IDENTIFIED BY 'change-me-audit';
GRANT SELECT, INSERT ON aurora.audit_event TO 'aurora_audit'@'%';
GRANT SELECT, INSERT ON aurora.trace_event TO 'aurora_audit'@'%';
REVOKE UPDATE, DELETE ON aurora.audit_event FROM 'aurora_audit'@'%';
REVOKE UPDATE, DELETE ON aurora.trace_event FROM 'aurora_audit'@'%';
FLUSH PRIVILEGES;
-- V5__append_only_audit.sql already installs BEFORE UPDATE/DELETE triggers, so
-- even the owner account cannot rewrite history without dropping the triggers.
