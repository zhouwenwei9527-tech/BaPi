ALTER TABLE approval_request ADD COLUMN intent_summary VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE approval_request ADD COLUMN operability INT NOT NULL DEFAULT 80;
ALTER TABLE approval_request ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE approval_request ADD COLUMN risk_reasons VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE approval_request ADD COLUMN requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE;
