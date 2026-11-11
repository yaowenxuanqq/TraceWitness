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
public class RetrievalStore {
    private final JdbcTemplate jdbc;

    public RetrievalStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countQueries(String runId) {
        Integer count = jdbc.queryForObject("select count(*) from retrieval_query where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count.intValue();
    }

    public void insertQuery(String id, String runId, String caseKey, int imageCount, String hardAttribute,
                            String softAttribute, boolean personCreated, String explainOrder) {
        jdbc.update("insert into retrieval_query(id, run_id, case_key, image_count, hard_attribute, soft_attribute, "
                        + "person_created, explain_order) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, imageCount, hardAttribute, softAttribute, personCreated, explainOrder);
    }

    public void insertHit(String id, String runId, String queryId, String candidateCode, int rankNo,
                          BigDecimal faceScore, BigDecimal bodyScore, BigDecimal fused, BigDecimal rankScore,
                          boolean softApplied, String explanation) {
        jdbc.update("insert into retrieval_hit(id, run_id, query_id, candidate_code, rank_no, face_score, body_score, "
                        + "fused, rank_score, soft_applied, explanation) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, queryId, candidateCode, rankNo, faceScore, bodyScore, fused, rankScore, softApplied, explanation);
    }

    public void insertExclusion(String id, String runId, String queryId, String candidateCode, String reason,
                                String explanation) {
        jdbc.update("insert into retrieval_exclusion(id, run_id, query_id, candidate_code, reason, explanation) "
                        + "values (?, ?, ?, ?, ?, ?)",
                id, runId, queryId, candidateCode, reason, explanation);
    }

    public void insertPassage(String id, String runId, String caseKey, String scene, int streak, boolean alerted,
                              String level, String status, String actionName, String actionStatus, boolean executed,
                              String detail) {
        jdbc.update("insert into passage_case(id, run_id, case_key, scene, streak, alerted, level, status, "
                        + "action_name, action_status, executed, detail) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, scene, streak, alerted, level, status, actionName, actionStatus, executed, detail);
    }

    public void updatePassageStatus(String id, String status) {
        jdbc.update("update passage_case set status = ? where id = ?", status, id);
    }

    public Map<String, Object> findPassage(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, case_key, scene, streak, alerted, level, status, "
                        + "action_name, action_status, executed, detail from passage_case where id = ?",
                (rs, rowNum) -> passageRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listQueries(String runId) {
        return jdbc.query("select id, run_id, case_key, image_count, hard_attribute, soft_attribute, person_created, "
                        + "explain_order from retrieval_query where run_id = ? order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("caseKey", rs.getString("case_key"));
                    row.put("imageCount", Integer.valueOf(rs.getInt("image_count")));
                    row.put("hardAttribute", rs.getString("hard_attribute"));
                    row.put("softAttribute", rs.getString("soft_attribute"));
                    row.put("personCreated", Boolean.valueOf(rs.getBoolean("person_created")));
                    row.put("explainOrder", rs.getString("explain_order"));
                    return row;
                }, runId);
    }

    public List<Map<String, Object>> listHits(String queryId) {
        return jdbc.query("select id, candidate_code, rank_no, face_score, body_score, fused, rank_score, "
                        + "soft_applied, explanation from retrieval_hit where query_id = ? order by rank_no, id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("candidateCode", rs.getString("candidate_code"));
                    row.put("rank", Integer.valueOf(rs.getInt("rank_no")));
                    row.put("faceScore", rs.getBigDecimal("face_score"));
                    row.put("bodyScore", rs.getBigDecimal("body_score"));
                    row.put("fused", rs.getBigDecimal("fused"));
                    row.put("rankScore", rs.getBigDecimal("rank_score"));
                    row.put("softApplied", Boolean.valueOf(rs.getBoolean("soft_applied")));
                    row.put("explanation", rs.getString("explanation"));
                    return row;
                }, queryId);
    }

    public List<Map<String, Object>> listExclusions(String queryId) {
        return jdbc.query("select id, candidate_code, reason, explanation from retrieval_exclusion "
                        + "where query_id = ? order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("candidateCode", rs.getString("candidate_code"));
                    row.put("reason", rs.getString("reason"));
                    row.put("explanation", rs.getString("explanation"));
                    return row;
                }, queryId);
    }

    public List<Map<String, Object>> listPassages(String runId) {
        return jdbc.query("select id, run_id, case_key, scene, streak, alerted, level, status, action_name, "
                        + "action_status, executed, detail from passage_case where run_id = ? order by id",
                (rs, rowNum) -> passageRow(rs), runId);
    }

    private static Map<String, Object> passageRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("caseKey", rs.getString("case_key"));
        row.put("scene", rs.getString("scene"));
        row.put("streak", Integer.valueOf(rs.getInt("streak")));
        row.put("alerted", Boolean.valueOf(rs.getBoolean("alerted")));
        row.put("level", rs.getString("level"));
        row.put("status", rs.getString("status"));
        row.put("actionName", rs.getString("action_name"));
        row.put("actionStatus", rs.getString("action_status"));
        row.put("executed", Boolean.valueOf(rs.getBoolean("executed")));
        row.put("detail", rs.getString("detail"));
        return row;
    }
}
