CREATE TABLE trajectory (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    profile_code VARCHAR(64) NOT NULL,
    kept_count INT NOT NULL,
    dropped_count INT NOT NULL,
    thin_count INT NOT NULL,
    UNIQUE (run_id, case_key)
);

CREATE TABLE trajectory_point (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    trajectory_id VARCHAR(160) NOT NULL,
    point_key VARCHAR(64) NOT NULL,
    door_code VARCHAR(64) NOT NULL,
    observed_second INT NOT NULL,
    x INT,
    y INT,
    kept BOOLEAN NOT NULL,
    thinned BOOLEAN NOT NULL,
    reason VARCHAR(64),
    seq_no INT NOT NULL
);

CREATE INDEX idx_trajectory_point ON trajectory_point (trajectory_id, seq_no);

CREATE TABLE companion_link (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    left_code VARCHAR(64) NOT NULL,
    right_code VARCHAR(64) NOT NULL,
    door_code VARCHAR(64) NOT NULL,
    gap_seconds INT NOT NULL
);

CREATE TABLE evidence_package (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    trajectory_id VARCHAR(160),
    point_keys VARCHAR(1000) NOT NULL,
    companion_text VARCHAR(1000) NOT NULL,
    source_ids VARCHAR(1000) NOT NULL,
    manifest VARCHAR(2000) NOT NULL,
    digest VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    UNIQUE (run_id, case_key)
);

CREATE TABLE hard_case (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    label VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    UNIQUE (run_id, case_key)
);

CREATE TABLE model_compare (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    left_name VARCHAR(64) NOT NULL,
    right_name VARCHAR(64) NOT NULL,
    left_score DECIMAL(8, 2) NOT NULL,
    right_score DECIMAL(8, 2) NOT NULL,
    winner VARCHAR(64) NOT NULL,
    trained BOOLEAN NOT NULL,
    UNIQUE (run_id, case_key)
);
