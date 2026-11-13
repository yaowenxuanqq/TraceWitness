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
public class TrajectoryStore {
    private final JdbcTemplate jdbc;

    public TrajectoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countTrajectories(String runId) {
        Integer count = jdbc.queryForObject("select count(*) from trajectory where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count.intValue();
    }

    public void insertTrajectory(String id, String runId, String caseKey, String profileCode, int keptCount,
                                 int droppedCount, int thinCount) {
        jdbc.update("insert into trajectory(id, run_id, case_key, profile_code, kept_count, dropped_count, thin_count) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, profileCode, keptCount, droppedCount, thinCount);
    }

    public void insertPoint(String id, String runId, String trajectoryId, String pointKey, String doorCode,
                            int observedSecond, Integer x, Integer y, boolean kept, boolean thinned, String reason,
                            int seqNo) {
        jdbc.update("insert into trajectory_point(id, run_id, trajectory_id, point_key, door_code, observed_second, "
                        + "x, y, kept, thinned, reason, seq_no) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, trajectoryId, pointKey, doorCode, observedSecond, x, y, kept, thinned, reason, seqNo);
    }

    public void insertCompanion(String id, String runId, String leftCode, String rightCode, String doorCode, int gapSeconds) {
        jdbc.update("insert into companion_link(id, run_id, left_code, right_code, door_code, gap_seconds) "
                        + "values (?, ?, ?, ?, ?, ?)",
                id, runId, leftCode, rightCode, doorCode, gapSeconds);
    }

    public void insertPackage(String id, String runId, String caseKey, String trajectoryId, String pointKeys,
                              String companionText, String sourceIds, String manifest, String digest, String status,
                              String errorCode) {
        jdbc.update("insert into evidence_package(id, run_id, case_key, trajectory_id, point_keys, companion_text, "
                        + "source_ids, manifest, digest, status, error_code) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, trajectoryId, pointKeys, companionText, sourceIds, manifest, digest, status, errorCode);
    }

    public void insertHardCase(String id, String runId, String caseKey, String reason, String label, String status) {
        jdbc.update("insert into hard_case(id, run_id, case_key, reason, label, status) values (?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, reason, label, status);
    }

    public void updateHardCase(String id, String label, String status) {
        jdbc.update("update hard_case set label = ?, status = ? where id = ?", label, status, id);
    }

    public void insertCompare(String id, String runId, String caseKey, String leftName, String rightName,
                              BigDecimal leftScore, BigDecimal rightScore, String winner, boolean trained) {
        jdbc.update("insert into model_compare(id, run_id, case_key, left_name, right_name, left_score, right_score, "
                        + "winner, trained) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, leftName, rightName, leftScore, rightScore, winner, trained);
    }

    public int countTable(String table, String runId) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count;
    }

    public int countPoints(String trajectoryId) {
        Integer count = jdbc.queryForObject("select count(*) from trajectory_point where trajectory_id = ?", Integer.class, trajectoryId);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> listTrajectories(String runId) {
        return listTrajectories(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listTrajectories(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, case_key, profile_code, kept_count, dropped_count, thin_count "
                        + "from trajectory where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> trajectoryRow(rs), runId, limit, offset);
    }

    public Map<String, Object> findTrajectory(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, case_key, profile_code, kept_count, dropped_count, thin_count "
                        + "from trajectory where id = ?",
                (rs, rowNum) -> trajectoryRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listPoints(String trajectoryId) {
        return listPoints(trajectoryId, Integer.MAX_VALUE);
    }

    public List<Map<String, Object>> listPoints(String trajectoryId, int limit) {
        return jdbc.query("select id, point_key, door_code, observed_second, x, y, kept, thinned, reason, seq_no "
                        + "from trajectory_point where trajectory_id = ? order by seq_no, id limit ?",
                (rs, rowNum) -> pointRow(rs), trajectoryId, limit);
    }

    public List<Map<String, Object>> listCompanions(String runId) {
        return listCompanions(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listCompanions(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, left_code, right_code, door_code, gap_seconds from companion_link "
                        + "where run_id = ? order by door_code, left_code, right_code, id limit ? offset ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("leftCode", rs.getString("left_code"));
                    row.put("rightCode", rs.getString("right_code"));
                    row.put("doorCode", rs.getString("door_code"));
                    row.put("gapSeconds", Integer.valueOf(rs.getInt("gap_seconds")));
                    return row;
                }, runId, limit, offset);
    }

    public List<Map<String, Object>> listPackages(String runId) {
        return listPackages(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listPackages(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, case_key, trajectory_id, point_keys, companion_text, source_ids, "
                        + "manifest, digest, status, error_code from evidence_package where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> packageRow(rs), runId, limit, offset);
    }

    public Map<String, Object> findPackage(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, case_key, trajectory_id, point_keys, companion_text, "
                        + "source_ids, manifest, digest, status, error_code from evidence_package where id = ?",
                (rs, rowNum) -> packageRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findPackageByCase(String runId, String caseKey) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, case_key, trajectory_id, point_keys, companion_text, "
                        + "source_ids, manifest, digest, status, error_code from evidence_package where run_id = ? and case_key = ?",
                (rs, rowNum) -> packageRow(rs), runId, caseKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listHardCases(String runId) {
        return listHardCases(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listHardCases(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, case_key, reason, label, status from hard_case where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> hardRow(rs), runId, limit, offset);
    }

    public Map<String, Object> findHardCase(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, case_key, reason, label, status from hard_case where id = ?",
                (rs, rowNum) -> hardRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listCompares(String runId) {
        return listCompares(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listCompares(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, case_key, left_name, right_name, left_score, right_score, winner, trained "
                        + "from model_compare where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("caseKey", rs.getString("case_key"));
                    row.put("leftName", rs.getString("left_name"));
                    row.put("rightName", rs.getString("right_name"));
                    row.put("leftScore", rs.getBigDecimal("left_score").toPlainString());
                    row.put("rightScore", rs.getBigDecimal("right_score").toPlainString());
                    row.put("winner", rs.getString("winner"));
                    row.put("trained", Boolean.valueOf(rs.getBoolean("trained")));
                    return row;
                }, runId, limit, offset);
    }

    private static Map<String, Object> trajectoryRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("caseKey", rs.getString("case_key"));
        row.put("profileCode", rs.getString("profile_code"));
        row.put("keptCount", Integer.valueOf(rs.getInt("kept_count")));
        row.put("droppedCount", Integer.valueOf(rs.getInt("dropped_count")));
        row.put("thinCount", Integer.valueOf(rs.getInt("thin_count")));
        return row;
    }

    private static Map<String, Object> pointRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("pointKey", rs.getString("point_key"));
        row.put("doorCode", rs.getString("door_code"));
        row.put("observedSecond", Integer.valueOf(rs.getInt("observed_second")));
        row.put("x", rs.getObject("x") == null ? null : Integer.valueOf(rs.getInt("x")));
        row.put("y", rs.getObject("y") == null ? null : Integer.valueOf(rs.getInt("y")));
        row.put("kept", Boolean.valueOf(rs.getBoolean("kept")));
        row.put("thinned", Boolean.valueOf(rs.getBoolean("thinned")));
        row.put("reason", rs.getString("reason"));
        row.put("seqNo", Integer.valueOf(rs.getInt("seq_no")));
        return row;
    }

    private static Map<String, Object> packageRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("caseKey", rs.getString("case_key"));
        row.put("trajectoryId", rs.getString("trajectory_id"));
        row.put("pointKeys", rs.getString("point_keys"));
        row.put("companionText", rs.getString("companion_text"));
        row.put("sourceIds", rs.getString("source_ids"));
        row.put("manifest", rs.getString("manifest"));
        row.put("digest", rs.getString("digest"));
        row.put("status", rs.getString("status"));
        row.put("errorCode", rs.getString("error_code"));
        return row;
    }

    private static Map<String, Object> hardRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("caseKey", rs.getString("case_key"));
        row.put("reason", rs.getString("reason"));
        row.put("label", rs.getString("label"));
        row.put("status", rs.getString("status"));
        return row;
    }
}
