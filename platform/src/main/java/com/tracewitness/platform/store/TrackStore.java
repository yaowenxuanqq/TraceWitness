package com.tracewitness.platform.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TrackStore {
    private final JdbcTemplate jdbc;

    public TrackStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countTracks(String runId) {
        Integer count = jdbc.queryForObject("select count(*) from track where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count;
    }

    public void insertTrack(String id, String runId, String deviceId, String localKey, int pointCount,
                             long openedTick, long closedTick) {
        jdbc.update("insert into track(id, run_id, device_id, local_key, point_count, opened_tick, closed_tick) values (?, ?, ?, ?, ?, ?, ?)",
                id, runId, deviceId, localKey, pointCount, openedTick, closedTick);
    }

    public void insertPoint(String id, String trackId, String runId, int frameIndex, long tick,
                             BigDecimal x, BigDecimal y, BigDecimal w, BigDecimal h, BigDecimal confidence) {
        jdbc.update("insert into track_point(id, track_id, run_id, frame_index, tick, x, y, w, h, confidence) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, trackId, runId, frameIndex, tick, x, y, w, h, confidence);
    }

    public void insertFault(String id, String runId, String deviceId, Integer frameIndex, Long tick,
                             String faultType, String detail, String createdAt) {
        jdbc.update("insert into inference_fault(id, run_id, device_id, frame_index, tick, fault_type, detail, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, deviceId, frameIndex, tick, faultType, detail, createdAt);
    }

    public List<Map<String, Object>> listFaults(String runId) {
        return jdbc.query("select id, frame_index, tick, fault_type, detail from inference_fault where run_id = ? order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("frameIndex", rs.getObject("frame_index") == null ? null : rs.getInt("frame_index"));
                    row.put("tick", rs.getObject("tick") == null ? null : rs.getLong("tick"));
                    row.put("faultType", rs.getString("fault_type"));
                    row.put("detail", rs.getString("detail"));
                    return row;
                }, runId);
    }

    public List<Map<String, Object>> listTracks(String runId) {
        List<Map<String, Object>> tracks = jdbc.query(
                "select id, run_id, device_id, local_key, point_count, opened_tick, closed_tick from track where run_id = ? order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("deviceId", rs.getString("device_id"));
                    row.put("localKey", rs.getString("local_key"));
                    row.put("pointCount", rs.getInt("point_count"));
                    row.put("openedTick", rs.getLong("opened_tick"));
                    row.put("closedTick", rs.getLong("closed_tick"));
                    row.put("points", new ArrayList<Map<String, Object>>());
                    return row;
                }, runId);
        Map<String, Map<String, Object>> byId = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> track : tracks) {
            byId.put(String.valueOf(track.get("id")), track);
        }
        jdbc.query("select id, track_id, frame_index, tick, x, y, w, h, confidence from track_point where run_id = ? order by track_id, frame_index, id",
                rs -> {
                    Map<String, Object> track = byId.get(rs.getString("track_id"));
                    if (track == null) {
                        return;
                    }
                    Map<String, Object> point = new LinkedHashMap<String, Object>();
                    point.put("id", rs.getString("id"));
                    point.put("frameIndex", rs.getInt("frame_index"));
                    point.put("tick", rs.getLong("tick"));
                    point.put("x", rs.getBigDecimal("x"));
                    point.put("y", rs.getBigDecimal("y"));
                    point.put("w", rs.getBigDecimal("w"));
                    point.put("h", rs.getBigDecimal("h"));
                    point.put("confidence", rs.getBigDecimal("confidence"));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> points = (List<Map<String, Object>>) track.get("points");
                    points.add(point);
                }, runId);
        return tracks;
    }
}
