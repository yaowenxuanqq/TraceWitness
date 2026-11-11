CREATE TABLE retrieval_query (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    image_count INT NOT NULL,
    hard_attribute VARCHAR(64),
    soft_attribute VARCHAR(64),
    person_created BOOLEAN NOT NULL,
    explain_order VARCHAR(128) NOT NULL,
    UNIQUE (run_id, case_key)
);

CREATE TABLE retrieval_hit (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    query_id VARCHAR(160) NOT NULL,
    candidate_code VARCHAR(64) NOT NULL,
    rank_no INT NOT NULL,
    face_score DECIMAL(8, 2),
    body_score DECIMAL(8, 2),
    fused DECIMAL(8, 2) NOT NULL,
    rank_score DECIMAL(8, 2) NOT NULL,
    soft_applied BOOLEAN NOT NULL,
    explanation VARCHAR(500) NOT NULL
);

CREATE INDEX idx_hit_query ON retrieval_hit (query_id, rank_no);

CREATE TABLE retrieval_exclusion (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    query_id VARCHAR(160) NOT NULL,
    candidate_code VARCHAR(64) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    explanation VARCHAR(500) NOT NULL
);

CREATE TABLE passage_case (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    case_key VARCHAR(64) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    streak INT NOT NULL,
    alerted BOOLEAN NOT NULL,
    level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    action_name VARCHAR(32),
    action_status VARCHAR(32) NOT NULL,
    executed BOOLEAN NOT NULL,
    detail VARCHAR(500) NOT NULL,
    UNIQUE (run_id, case_key)
);
