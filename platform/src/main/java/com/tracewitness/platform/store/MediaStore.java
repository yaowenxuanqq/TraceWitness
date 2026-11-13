package com.tracewitness.platform.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MediaStore {
    private static final String ASSET_COLUMNS = "select id, run_id, device_id, event_id, kind, source_kind, content_type, storage_path, start_tick, end_tick, sample_rate, sample_count, audio_continuous, created_at, status, sha256, size_bytes from media_asset";

    private final JdbcTemplate jdbc;

    public MediaStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countKind(String runId, String kind) {
        Integer count = jdbc.queryForObject(
                "select count(*) from media_asset where run_id = ? and kind = ?",
                Integer.class,
                runId,
                kind);
        return count == null ? 0 : count;
    }

    public void insertAsset(String id, String runId, String deviceId, String eventId, String kind, String sourceKind,
                             String contentType, String storagePath, Long startTick, Long endTick,
                             Integer sampleRate, Long sampleCount, Boolean audioContinuous, String createdAt) {
        jdbc.update("insert into media_asset(id, run_id, device_id, event_id, kind, source_kind, content_type, storage_path, start_tick, end_tick, sample_rate, sample_count, audio_continuous, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, deviceId, eventId, kind, sourceKind, contentType, storagePath, startTick, endTick,
                sampleRate, sampleCount, audioContinuous, createdAt);
    }

    public void insertSegment(String id, String runId, String deviceId, long tick, String profile, int fps,
                               int frameCount, long audioStartSample, int audioSampleCount, String frameDir) {
        jdbc.update("insert into media_segment(id, run_id, device_id, tick, profile, fps, frame_count, audio_start_sample, audio_sample_count, frame_dir) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, deviceId, tick, profile, fps, frameCount, audioStartSample, audioSampleCount, frameDir);
    }

    public void insertUploading(String id, String runId, String contentType, String storagePath, String createdAt) {
        jdbc.update("insert into media_asset(id, run_id, device_id, event_id, kind, source_kind, content_type, storage_path, start_tick, end_tick, sample_rate, sample_count, audio_continuous, created_at, status) values (?, ?, null, null, 'UPLOAD', 'FILE', ?, ?, null, null, null, null, null, ?, 'UPLOADING')",
                id, runId, contentType, storagePath, createdAt);
    }

    public void updateState(String id, String status, String sha256, Long sizeBytes) {
        jdbc.update("update media_asset set status = ?, sha256 = coalesce(?, sha256), size_bytes = coalesce(?, size_bytes) where id = ?",
                status, sha256, sizeBytes, id);
    }

    public List<Map<String, Object>> listRecoverable() {
        return jdbc.query(ASSET_COLUMNS + " where status in ('UPLOADING', 'STAGED', 'READY') order by created_at, id",
                (rs, rowNum) -> assetRow(rs));
    }

    public Map<String, Object> findAsset(String id) {
        List<Map<String, Object>> rows = jdbc.query(ASSET_COLUMNS + " where id = ?",
                (rs, rowNum) -> assetRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listAssets(String runId, String kind) {
        return jdbc.query(ASSET_COLUMNS + " where run_id = ? and kind = ? and status = 'READY' order by start_tick, id",
                (rs, rowNum) -> assetRow(rs), runId, kind);
    }

    public List<Map<String, Object>> listUploads(String runId) {
        return jdbc.query(ASSET_COLUMNS + " where run_id = ? and kind = 'UPLOAD' and status = 'READY' order by created_at, id",
                (rs, rowNum) -> assetRow(rs), runId);
    }

    public List<Map<String, Object>> listSegments(String runId) {
        return jdbc.query("select id, run_id, device_id, tick, profile, fps, frame_count, audio_start_sample, audio_sample_count, frame_dir from media_segment where run_id = ? order by device_id, tick, id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("deviceId", rs.getString("device_id"));
                    row.put("tick", rs.getLong("tick"));
                    row.put("profile", rs.getString("profile"));
                    row.put("fps", rs.getInt("fps"));
                    row.put("frameCount", rs.getInt("frame_count"));
                    row.put("audioStartSample", rs.getLong("audio_start_sample"));
                    row.put("audioSampleCount", rs.getInt("audio_sample_count"));
                    row.put("frameDir", rs.getString("frame_dir"));
                    return row;
                }, runId);
    }

    public Map<String, Object> findSegment(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, device_id, tick, profile, fps, frame_count, audio_start_sample, audio_sample_count, frame_dir from media_segment where id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("deviceId", rs.getString("device_id"));
                    row.put("tick", rs.getLong("tick"));
                    row.put("profile", rs.getString("profile"));
                    row.put("fps", rs.getInt("fps"));
                    row.put("frameCount", rs.getInt("frame_count"));
                    row.put("audioStartSample", rs.getLong("audio_start_sample"));
                    row.put("audioSampleCount", rs.getInt("audio_sample_count"));
                    row.put("frameDir", rs.getString("frame_dir"));
                    return row;
                }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Map<String, Object> assetRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("deviceId", rs.getString("device_id"));
        row.put("eventId", rs.getString("event_id"));
        row.put("kind", rs.getString("kind"));
        row.put("sourceKind", rs.getString("source_kind"));
        row.put("contentType", rs.getString("content_type"));
        row.put("storagePath", rs.getString("storage_path"));
        row.put("startTick", rs.getObject("start_tick") == null ? null : rs.getLong("start_tick"));
        row.put("endTick", rs.getObject("end_tick") == null ? null : rs.getLong("end_tick"));
        row.put("sampleRate", rs.getObject("sample_rate") == null ? null : rs.getInt("sample_rate"));
        row.put("sampleCount", rs.getObject("sample_count") == null ? null : rs.getLong("sample_count"));
        row.put("audioContinuous", rs.getObject("audio_continuous") == null ? null : rs.getBoolean("audio_continuous"));
        row.put("createdAt", rs.getString("created_at"));
        row.put("status", rs.getString("status"));
        row.put("sha256", rs.getString("sha256"));
        row.put("sizeBytes", rs.getObject("size_bytes") == null ? null : Long.valueOf(rs.getLong("size_bytes")));
        return row;
    }
}
