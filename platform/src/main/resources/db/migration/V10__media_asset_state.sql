ALTER TABLE media_asset ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'READY';
ALTER TABLE media_asset ADD COLUMN sha256 VARCHAR(64);
ALTER TABLE media_asset ADD COLUMN size_bytes BIGINT;

CREATE INDEX idx_media_run_status
    ON media_asset (run_id, kind, status, created_at, id);
