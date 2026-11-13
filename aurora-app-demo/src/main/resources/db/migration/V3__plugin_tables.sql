-- Real side effect for the lab-data plugin (used to demonstrate approval execution).
CREATE TABLE dataset_import_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    dataset_name VARCHAR(200) NOT NULL,
    row_count INT NOT NULL,
    imported_by BIGINT NOT NULL,
    trace_id VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
