package com.tracewitness.platform.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ObservationStore {
    private final JdbcTemplate jdbc;

    public ObservationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count(String runId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from structured_observation where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count;
    }

    public void insert(String id, String runId, String trackId, String caseKey, String mode, String frameKind,
                        int chosenFrame, long chosenTick, int attributeCalls, BigDecimal faceQuality,
                        BigDecimal bodyQuality, String attributesJson, String maskedJson, String thumbnailPath,
                        String createdAt) {
        jdbc.update("insert into structured_observation(id, run_id, track_id, case_key, mode, frame_kind, "
                        + "chosen_frame, chosen_tick, attribute_calls, face_quality, body_quality, attributes_json, "
                        + "masked_json, thumbnail_path, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, trackId, caseKey, mode, frameKind, chosenFrame, chosenTick, attributeCalls,
                faceQuality, bodyQuality, attributesJson, maskedJson, thumbnailPath, createdAt);
    }

    public List<Map<String, Object>> list(String runId) {
        return jdbc.query("select id, run_id, track_id, case_key, mode, frame_kind, chosen_frame, chosen_tick, "
                        + "attribute_calls, face_quality, body_quality, attributes_json, masked_json, thumbnail_path, created_at "
                        + "from structured_observation where run_id = ? order by id",
                (rs, rowNum) -> row(rs), runId);
    }

    public Map<String, Object> find(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, track_id, case_key, mode, frame_kind, "
                        + "chosen_frame, chosen_tick, attribute_calls, face_quality, body_quality, attributes_json, "
                        + "masked_json, thumbnail_path, created_at from structured_observation where id = ?",
                (rs, rowNum) -> row(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("trackId", rs.getString("track_id"));
        row.put("caseKey", rs.getString("case_key"));
        row.put("mode", rs.getString("mode"));
        row.put("frameKind", rs.getString("frame_kind"));
        row.put("chosenFrame", rs.getInt("chosen_frame"));
        row.put("chosenTick", rs.getLong("chosen_tick"));
        row.put("attributeCalls", rs.getInt("attribute_calls"));
        row.put("faceQuality", rs.getBigDecimal("face_quality"));
        row.put("bodyQuality", rs.getBigDecimal("body_quality"));
        row.put("attributesJson", rs.getString("attributes_json"));
        row.put("maskedJson", rs.getString("masked_json"));
        row.put("thumbnailPath", rs.getString("thumbnail_path"));
        row.put("createdAt", rs.getString("created_at"));
        return row;
    }
}
