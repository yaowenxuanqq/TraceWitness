CREATE TABLE plugin_manifest (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    name VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    arch VARCHAR(32) NOT NULL,
    abi VARCHAR(32) NOT NULL,
    resource_mb INT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    signature VARCHAR(128) NOT NULL,
    payload VARCHAR(500) NOT NULL,
    slot VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    health DECIMAL(8, 2) NOT NULL,
    loaded BOOLEAN NOT NULL,
    hash_checked BOOLEAN NOT NULL,
    check_order VARCHAR(64) NOT NULL
);

CREATE INDEX idx_plugin_slot ON plugin_manifest (run_id, slot);

CREATE TABLE plugin_health (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    manifest_id VARCHAR(160) NOT NULL,
    score DECIMAL(8, 2) NOT NULL,
    signature_ok BOOLEAN NOT NULL,
    hash_ok BOOLEAN NOT NULL,
    abi_ok BOOLEAN NOT NULL,
    resource_ok BOOLEAN NOT NULL
);

CREATE TABLE plugin_rollback (
    id VARCHAR(160) PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    from_id VARCHAR(160) NOT NULL,
    to_id VARCHAR(160),
    reason VARCHAR(64) NOT NULL,
    actor VARCHAR(64) NOT NULL
);
