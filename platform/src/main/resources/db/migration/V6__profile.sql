CREATE TABLE door_node (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    UNIQUE (run_id, code)
);

CREATE TABLE door_edge (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    from_code VARCHAR(64) NOT NULL,
    to_code VARCHAR(64) NOT NULL,
    travel_seconds INT NOT NULL
);

CREATE TABLE person_profile (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    code VARCHAR(64) NOT NULL,
    gallery VARCHAR(16) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    address_label VARCHAR(64),
    centroid VARCHAR(200) NOT NULL,
    quality DECIMAL(8, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    merged_into VARCHAR(160),
    visit_count INT NOT NULL,
    UNIQUE (run_id, code)
);

CREATE TABLE profile_sample (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    profile_id VARCHAR(160),
    case_key VARCHAR(64) NOT NULL,
    face_quality DECIMAL(8, 2) NOT NULL,
    vector VARCHAR(200) NOT NULL,
    bound BOOLEAN NOT NULL
);

CREATE TABLE access_event (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    door_code VARCHAR(64) NOT NULL,
    tick BIGINT NOT NULL,
    sample_id VARCHAR(160) NOT NULL,
    occurred_at VARCHAR(32) NOT NULL
);

CREATE INDEX idx_access_run ON access_event (run_id, tick, id);

CREATE TABLE profile_event (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    profile_id VARCHAR(160),
    access_event_id VARCHAR(160) NOT NULL,
    sample_id VARCHAR(160) NOT NULL,
    bind_status VARCHAR(32) NOT NULL
);

CREATE TABLE merge_ticket (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    ticket_kind VARCHAR(16) NOT NULL,
    left_profile_id VARCHAR(160),
    right_profile_id VARCHAR(160),
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    observed_seconds INT,
    travel_seconds INT,
    detail VARCHAR(500) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    UNIQUE (run_id, case_key)
);

CREATE TABLE audit_log (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_id VARCHAR(160) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at VARCHAR(32) NOT NULL
);

CREATE INDEX idx_audit_run ON audit_log (run_id, id);
