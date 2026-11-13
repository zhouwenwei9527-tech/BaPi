-- Longer status names are needed for explicit risk-confirmation outcomes.
ALTER TABLE tool_call_record MODIFY COLUMN status VARCHAR(32) NOT NULL;
