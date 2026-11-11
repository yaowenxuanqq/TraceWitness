CREATE TABLE structured_observation (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    track_id VARCHAR(160),
    case_key VARCHAR(64) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    frame_kind VARCHAR(16) NOT NULL,
    chosen_frame INT NOT NULL,
    chosen_tick BIGINT NOT NULL,
    attribute_calls INT NOT NULL,
    face_quality DECIMAL(8, 2) NOT NULL,
    body_quality DECIMAL(8, 2) NOT NULL,
    attributes_json VARCHAR(2000) NOT NULL,
    masked_json VARCHAR(2000) NOT NULL,
    thumbnail_path VARCHAR(500) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    UNIQUE (run_id, case_key)
);

CREATE INDEX idx_observation_run ON structured_observation (run_id, id);
