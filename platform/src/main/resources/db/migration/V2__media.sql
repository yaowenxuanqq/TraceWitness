CREATE TABLE media_asset (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    device_id VARCHAR(160),
    event_id VARCHAR(160),
    kind VARCHAR(16) NOT NULL,
    source_kind VARCHAR(16) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    start_tick BIGINT,
    end_tick BIGINT,
    sample_rate INT,
    sample_count BIGINT,
    audio_continuous BOOLEAN,
    created_at VARCHAR(32) NOT NULL
);

CREATE INDEX idx_media_run ON media_asset (run_id, kind, id);

CREATE TABLE media_segment (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    device_id VARCHAR(160) NOT NULL,
    tick BIGINT NOT NULL,
    profile VARCHAR(8) NOT NULL,
    fps INT NOT NULL,
    frame_count INT NOT NULL,
    audio_start_sample BIGINT NOT NULL,
    audio_sample_count INT NOT NULL,
    frame_dir VARCHAR(500) NOT NULL
);

CREATE INDEX idx_segment_run ON media_segment (run_id, device_id, tick);
