CREATE TABLE track (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    device_id VARCHAR(160) NOT NULL,
    local_key VARCHAR(32) NOT NULL,
    point_count INT NOT NULL,
    opened_tick BIGINT NOT NULL,
    closed_tick BIGINT NOT NULL
);

CREATE INDEX idx_track_run ON track (run_id, id);

CREATE TABLE track_point (
    id VARCHAR(160) PRIMARY KEY,
    track_id VARCHAR(160) NOT NULL,
    run_id VARCHAR(80) NOT NULL,
    frame_index INT NOT NULL,
    tick BIGINT NOT NULL,
    x DECIMAL(8, 2) NOT NULL,
    y DECIMAL(8, 2) NOT NULL,
    w DECIMAL(8, 2) NOT NULL,
    h DECIMAL(8, 2) NOT NULL,
    confidence DECIMAL(8, 2) NOT NULL
);

CREATE INDEX idx_track_point ON track_point (track_id, frame_index);

CREATE TABLE inference_fault (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    device_id VARCHAR(160),
    frame_index INT,
    tick BIGINT,
    fault_type VARCHAR(64) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at VARCHAR(32) NOT NULL
);

CREATE INDEX idx_fault_run ON inference_fault (run_id, id);
