-- MySQL-only hardening: audit and trace are append-only at the database level.
-- H2 test databases skip this location because they cannot express SIGNAL.
DROP TRIGGER IF EXISTS audit_event_no_update;
DROP TRIGGER IF EXISTS audit_event_no_delete;
DROP TRIGGER IF EXISTS trace_event_no_update;
DROP TRIGGER IF EXISTS trace_event_no_delete;

CREATE TRIGGER audit_event_no_update BEFORE UPDATE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only';

CREATE TRIGGER audit_event_no_delete BEFORE DELETE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is append-only';

CREATE TRIGGER trace_event_no_update BEFORE UPDATE ON trace_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'trace_event is append-only';

CREATE TRIGGER trace_event_no_delete BEFORE DELETE ON trace_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'trace_event is append-only';
