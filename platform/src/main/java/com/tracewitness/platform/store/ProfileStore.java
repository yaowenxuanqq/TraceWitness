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
public class ProfileStore {
    private final JdbcTemplate jdbc;

    public ProfileStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countProfiles(String runId) {
        Integer count = jdbc.queryForObject("select count(*) from person_profile where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count.intValue();
    }

    public void insertDoor(String id, String runId, String code, String name) {
        jdbc.update("insert into door_node(id, run_id, code, name) values (?, ?, ?, ?)", id, runId, code, name);
    }

    public void insertEdge(String id, String runId, String fromCode, String toCode, int travelSeconds) {
        jdbc.update("insert into door_edge(id, run_id, from_code, to_code, travel_seconds) values (?, ?, ?, ?, ?)",
                id, runId, fromCode, toCode, travelSeconds);
    }

    public void insertProfile(String id, String runId, String code, String gallery, String displayName,
                              String addressLabel, String centroid, BigDecimal quality, String status, int visitCount) {
        jdbc.update("insert into person_profile(id, run_id, code, gallery, display_name, address_label, centroid, "
                        + "quality, status, merged_into, visit_count) values (?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?)",
                id, runId, code, gallery, displayName, addressLabel, centroid, quality, status, visitCount);
    }

    public void updateProfile(String id, String addressLabel, String centroid, BigDecimal quality, int visitCount) {
        jdbc.update("update person_profile set address_label = ?, centroid = ?, quality = ?, visit_count = ? where id = ?",
                addressLabel, centroid, quality, visitCount, id);
    }

    public void markMerged(String id, String mergedInto) {
        jdbc.update("update person_profile set status = 'merged', merged_into = ? where id = ?", mergedInto, id);
    }

    public void insertSample(String id, String runId, String profileId, String caseKey, BigDecimal faceQuality,
                             String vector, boolean bound) {
        jdbc.update("insert into profile_sample(id, run_id, profile_id, case_key, face_quality, vector, bound) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, runId, profileId, caseKey, faceQuality, vector, bound);
    }

    public void reassignSamples(String fromProfileId, String toProfileId) {
        jdbc.update("update profile_sample set profile_id = ? where profile_id = ? and bound = true",
                toProfileId, fromProfileId);
    }

    public void insertAccess(String id, String runId, String caseKey, String doorCode, long tick, String sampleId,
                             String occurredAt) {
        jdbc.update("insert into access_event(id, run_id, case_key, door_code, tick, sample_id, occurred_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, doorCode, tick, sampleId, occurredAt);
    }

    public void insertBinding(String id, String runId, String profileId, String accessEventId, String sampleId,
                              String bindStatus) {
        jdbc.update("insert into profile_event(id, run_id, profile_id, access_event_id, sample_id, bind_status) "
                        + "values (?, ?, ?, ?, ?, ?)",
                id, runId, profileId, accessEventId, sampleId, bindStatus);
    }

    public void insertTicket(String id, String runId, String caseKey, String ticketKind, String leftProfileId,
                             String rightProfileId, String status, String reason, Integer observedSeconds,
                             Integer travelSeconds, String detail, String createdAt) {
        jdbc.update("insert into merge_ticket(id, run_id, case_key, ticket_kind, left_profile_id, right_profile_id, "
                        + "status, reason, observed_seconds, travel_seconds, detail, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, caseKey, ticketKind, leftProfileId, rightProfileId, status, reason, observedSeconds,
                travelSeconds, detail, createdAt);
    }

    public void updateTicketStatus(String id, String status) {
        jdbc.update("update merge_ticket set status = ? where id = ?", status, id);
    }

    public void insertAudit(String id, String runId, String action, String targetId, String detail, String actor,
                            String createdAt) {
        jdbc.update("insert into audit_log(id, run_id, action, target_id, detail, actor, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                id, runId, action, targetId, detail, actor, createdAt);
    }

    public Map<String, Object> findProfileByCode(String runId, String code) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, code, gallery, display_name, address_label, "
                        + "centroid, quality, status, merged_into, visit_count from person_profile "
                        + "where run_id = ? and code = ?",
                (rs, rowNum) -> profileRow(rs), runId, code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findProfile(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, code, gallery, display_name, address_label, "
                        + "centroid, quality, status, merged_into, visit_count from person_profile where id = ?",
                (rs, rowNum) -> profileRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findTicket(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, case_key, ticket_kind, left_profile_id, "
                        + "right_profile_id, status, reason, observed_seconds, travel_seconds, detail, created_at "
                        + "from merge_ticket where id = ?",
                (rs, rowNum) -> ticketRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int countTable(String table, String runId) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> listProfiles(String runId) {
        return listProfiles(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listProfiles(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, code, gallery, display_name, address_label, centroid, quality, status, "
                        + "merged_into, visit_count from person_profile where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> profileRow(rs), runId, limit, offset);
    }

    public List<Map<String, Object>> listDoors(String runId) {
        return listDoors(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listDoors(String runId, int limit, int offset) {
        return jdbc.query("select id, code, name from door_node where run_id = ? order by id limit ? offset ?", (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", rs.getString("id"));
            row.put("code", rs.getString("code"));
            row.put("name", rs.getString("name"));
                    return row;
                }, runId, limit, offset);
    }

    public List<Map<String, Object>> listEdges(String runId) {
        return listEdges(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listEdges(String runId, int limit, int offset) {
        return jdbc.query("select id, from_code, to_code, travel_seconds from door_edge where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("fromCode", rs.getString("from_code"));
                    row.put("toCode", rs.getString("to_code"));
                    row.put("travelSeconds", Integer.valueOf(rs.getInt("travel_seconds")));
                    return row;
                }, runId, limit, offset);
    }

    public List<Map<String, Object>> listTickets(String runId) {
        return listTickets(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listTickets(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, case_key, ticket_kind, left_profile_id, right_profile_id, status, reason, "
                        + "observed_seconds, travel_seconds, detail, created_at from merge_ticket where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> ticketRow(rs), runId, limit, offset);
    }

    public List<Map<String, Object>> listAudits(String runId) {
        return listAudits(runId, Integer.MAX_VALUE, 0);
    }

    public List<Map<String, Object>> listAudits(String runId, int limit, int offset) {
        return jdbc.query("select id, run_id, action, target_id, detail, actor, created_at from audit_log "
                        + "where run_id = ? order by id limit ? offset ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("action", rs.getString("action"));
                    row.put("targetId", rs.getString("target_id"));
                    row.put("detail", rs.getString("detail"));
                    row.put("actor", rs.getString("actor"));
                    row.put("createdAt", rs.getString("created_at"));
                    return row;
                }, runId, limit, offset);
    }

    public List<Map<String, Object>> listBoundSamples(String profileId) {
        return jdbc.query("select id, face_quality, vector from profile_sample where profile_id = ? and bound = true order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("faceQuality", rs.getBigDecimal("face_quality"));
                    row.put("vector", rs.getString("vector"));
                    return row;
                }, profileId);
    }

    public List<String> listBoundDoors(String profileId) {
        return jdbc.query("select a.door_code from profile_event e join access_event a on a.id = e.access_event_id "
                        + "where e.profile_id = ? and e.bind_status = 'bound' order by a.tick, a.id",
                (rs, rowNum) -> rs.getString("door_code"), profileId);
    }

    public int countRejected(String runId, String caseKey) {
        Integer count = jdbc.queryForObject("select count(*) from profile_event e join access_event a "
                        + "on a.id = e.access_event_id where a.run_id = ? and a.case_key = ? and e.bind_status = 'rejected'",
                Integer.class, runId, caseKey);
        return count == null ? 0 : count.intValue();
    }

    private static Map<String, Object> profileRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("code", rs.getString("code"));
        row.put("gallery", rs.getString("gallery"));
        row.put("displayName", rs.getString("display_name"));
        row.put("addressLabel", rs.getString("address_label"));
        row.put("centroid", rs.getString("centroid"));
        row.put("quality", rs.getBigDecimal("quality"));
        row.put("status", rs.getString("status"));
        row.put("mergedInto", rs.getString("merged_into"));
        row.put("visitCount", Integer.valueOf(rs.getInt("visit_count")));
        return row;
    }

    private static Map<String, Object> ticketRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("caseKey", rs.getString("case_key"));
        row.put("ticketKind", rs.getString("ticket_kind"));
        row.put("leftProfileId", rs.getString("left_profile_id"));
        row.put("rightProfileId", rs.getString("right_profile_id"));
        row.put("status", rs.getString("status"));
        row.put("reason", rs.getString("reason"));
        int observed = rs.getInt("observed_seconds");
        row.put("observedSeconds", rs.wasNull() ? null : Integer.valueOf(observed));
        int travel = rs.getInt("travel_seconds");
        row.put("travelSeconds", rs.wasNull() ? null : Integer.valueOf(travel));
        row.put("detail", rs.getString("detail"));
        row.put("createdAt", rs.getString("created_at"));
        return row;
    }
}
