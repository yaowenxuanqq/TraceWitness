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
public class DecisionStore {
    private final JdbcTemplate jdbc;

    public DecisionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count(String runId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from decision_snapshot where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count;
    }

    public void insert(String id, String runId, String caseKey, String observationId, String policyVersion,
                       String clockMode, String business, BigDecimal faceQuality, BigDecimal bodyQuality,
                       BigDecimal continuity, int historyCount, BigDecimal qObserved, BigDecimal qScripted,
                       BigDecimal qInput, BigDecimal thetaUEff, BigDecimal uncertainty, boolean degraded,
                       String route, String reason, String fusion, String conclusion, boolean cloudCalled,
                       String localLabel, BigDecimal localScore, String cloudLabel, BigDecimal cloudScore,
                       String decidedAt) {
        jdbc.update("insert into decision_snapshot(id, run_id, case_key, observation_id, policy_version, clock_mode, "
                        + "business, face_quality, body_quality, continuity, history_count, q_observed, q_scripted, "
                        + "q_input, theta_u_eff, uncertainty, degraded, route, reason, fusion, conclusion, cloud_called, "
                        + "local_label, local_score, cloud_label, cloud_score, decided_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, observationId, policyVersion, clockMode, business, faceQuality, bodyQuality,
                continuity, historyCount, qObserved, qScripted, qInput, thetaUEff, uncertainty, degraded, route, reason,
                fusion, conclusion, cloudCalled, localLabel, localScore, cloudLabel, cloudScore, decidedAt);
    }

    public List<Map<String, Object>> list(String runId) {
        return jdbc.query("select id, run_id, case_key, observation_id, policy_version, clock_mode, business, "
                        + "face_quality, body_quality, continuity, history_count, q_observed, q_scripted, q_input, "
                        + "theta_u_eff, uncertainty, degraded, route, reason, fusion, conclusion, cloud_called, "
                        + "local_label, local_score, cloud_label, cloud_score, decided_at "
                        + "from decision_snapshot where run_id = ? order by id",
                (rs, rowNum) -> row(rs), runId);
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("caseKey", rs.getString("case_key"));
        row.put("observationId", rs.getString("observation_id"));
        row.put("policyVersion", rs.getString("policy_version"));
        row.put("clockMode", rs.getString("clock_mode"));
        row.put("business", rs.getString("business"));
        row.put("faceQuality", rs.getBigDecimal("face_quality"));
        row.put("bodyQuality", rs.getBigDecimal("body_quality"));
        row.put("continuity", rs.getBigDecimal("continuity"));
        row.put("historyCount", rs.getInt("history_count"));
        row.put("qObserved", rs.getBigDecimal("q_observed"));
        row.put("qScripted", rs.getBigDecimal("q_scripted"));
        row.put("qInput", rs.getBigDecimal("q_input"));
        row.put("thetaUEff", rs.getBigDecimal("theta_u_eff"));
        row.put("uncertainty", rs.getBigDecimal("uncertainty"));
        row.put("degraded", rs.getBoolean("degraded"));
        row.put("route", rs.getString("route"));
        row.put("reason", rs.getString("reason"));
        row.put("fusion", rs.getString("fusion"));
        row.put("conclusion", rs.getString("conclusion"));
        row.put("cloudCalled", rs.getBoolean("cloud_called"));
        row.put("localLabel", rs.getString("local_label"));
        row.put("localScore", rs.getBigDecimal("local_score"));
        row.put("cloudLabel", rs.getString("cloud_label"));
        row.put("cloudScore", rs.getBigDecimal("cloud_score"));
        row.put("decidedAt", rs.getString("decided_at"));
        return row;
    }
}
