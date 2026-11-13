-- Aurora / BaPi schema. All tables carry tenant_id (v1.0 fixed to 0).
CREATE TABLE sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(64) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    dept_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    title VARCHAR(64) NOT NULL DEFAULT '',
    avatar_hue INT NOT NULL DEFAULT 210,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(200) NOT NULL DEFAULT ''
);

CREATE TABLE sys_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    parent_code VARCHAR(100) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    icon VARCHAR(32) NOT NULL DEFAULT 'square',
    description VARCHAR(200) NOT NULL DEFAULT ''
);

CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE (user_id, role_id)
);

CREATE TABLE sys_role_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    role_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    UNIQUE (role_id, resource_id)
);

CREATE TABLE sys_data_scope_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    role_id BIGINT NOT NULL,
    type VARCHAR(24) NOT NULL,
    custom_dept_ids VARCHAR(500) NOT NULL DEFAULT ''
);

CREATE TABLE sys_session (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    active_role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL
);

-- Append-only audit: no UPDATE/DELETE anywhere in application code.
CREATE TABLE audit_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    ts TIMESTAMP(6) NOT NULL,
    trace_id VARCHAR(64) NULL,
    session_id VARCHAR(64) NULL,
    type VARCHAR(24) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    agent_id VARCHAR(64) NULL,
    user_id BIGINT NULL,
    username VARCHAR(64) NULL,
    roles TEXT NULL,
    tool_name VARCHAR(100) NULL,
    args_digest VARCHAR(64) NULL,
    decision VARCHAR(48) NULL,
    approval_id VARCHAR(36) NULL,
    latency_ms BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    refs_json TEXT NULL,
    prev_hash VARCHAR(64) NULL,
    hash VARCHAR(64) NOT NULL
);
CREATE INDEX idx_audit_trace ON audit_event(trace_id);
CREATE INDEX idx_audit_tool ON audit_event(tool_name);

CREATE TABLE trace_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    trace_id VARCHAR(64) NOT NULL,
    ts TIMESTAMP(6) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    decision VARCHAR(200) NULL,
    detail_json TEXT NULL
);
CREATE INDEX idx_trace_trace ON trace_event(trace_id);

CREATE TABLE approval_request (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    tool_name VARCHAR(100) NOT NULL,
    tool_version VARCHAR(24) NOT NULL,
    args_json TEXT NOT NULL,
    args_hash VARCHAR(64) NOT NULL,
    args_summary VARCHAR(500) NOT NULL,
    affected_objects VARCHAR(500) NULL,
    affected_count INT NOT NULL DEFAULT 0,
    side_effect VARCHAR(16) NOT NULL,
    reversible VARCHAR(64) NOT NULL,
    requester_user_id BIGINT NOT NULL,
    requester_name VARCHAR(64) NOT NULL,
    requester_role_id BIGINT NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    agent_id VARCHAR(64) NULL,
    session_id VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    decided_by VARCHAR(64) NULL,
    decided_at TIMESTAMP NULL,
    executed_at TIMESTAMP NULL,
    result_json TEXT NULL,
    error_code VARCHAR(48) NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_approval_status ON approval_request(status);

CREATE TABLE agent_session (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE agent_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    tool_calls_json TEXT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_message_session ON agent_message(session_id);

CREATE TABLE tool_call_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    session_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    args_digest VARCHAR(64) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    approval_id VARCHAR(36) NULL,
    summary VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_toolcall_user ON tool_call_record(user_id);

CREATE TABLE agent_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    memory_key VARCHAR(120) NOT NULL,
    memory_value TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE model_call_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    session_id VARCHAR(36) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    seq INT NOT NULL,
    provider VARCHAR(64) NOT NULL,
    request_json TEXT NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_modelcall_trace ON model_call_record(trace_id);

CREATE TABLE kb_document (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(300) NOT NULL,
    summary TEXT NOT NULL,
    content TEXT NOT NULL,
    object_type VARCHAR(40) NOT NULL,
    object_id VARCHAR(80) NOT NULL,
    owner_id BIGINT NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    visible_roles VARCHAR(300) NOT NULL DEFAULT '',
    visible_depts VARCHAR(300) NOT NULL DEFAULT '',
    acl_version INT NOT NULL DEFAULT 1,
    page_no INT NOT NULL DEFAULT 1,
    para_no INT NOT NULL DEFAULT 1,
    required_permission VARCHAR(100) NULL,
    storage_key VARCHAR(200) NULL,
    mime_type VARCHAR(100) NOT NULL DEFAULT 'text/markdown',
    file_name VARCHAR(300) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_kb_owner ON kb_document(owner_id);
