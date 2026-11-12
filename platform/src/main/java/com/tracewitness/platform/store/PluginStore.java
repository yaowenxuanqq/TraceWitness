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
public class PluginStore {
    private final JdbcTemplate jdbc;

    public PluginStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count(String runId) {
        Integer count = jdbc.queryForObject("select count(*) from plugin_manifest where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count.intValue();
    }

    public void insertManifest(String id, String runId, String name, String version, String arch, String abi,
                               int resourceMb, String payloadHash, String signature, String payload, String slot,
                               String reason, BigDecimal health, boolean loaded, boolean hashChecked, String checkOrder) {
        jdbc.update("insert into plugin_manifest(id, run_id, name, version, arch, abi, resource_mb, payload_hash, "
                        + "signature, payload, slot, reason, health, loaded, hash_checked, check_order) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, name, version, arch, abi, resourceMb, payloadHash, signature, payload, slot, reason,
                health, loaded, hashChecked, checkOrder);
    }

    public void updateOutcome(String id, String signature, String slot, String reason, BigDecimal health,
                              boolean loaded, boolean hashChecked) {
        jdbc.update("update plugin_manifest set signature = ?, slot = ?, reason = ?, health = ?, loaded = ?, "
                        + "hash_checked = ? where id = ?",
                signature, slot, reason, health, loaded, hashChecked, id);
    }

    public void updateSlot(String id, String slot) {
        jdbc.update("update plugin_manifest set slot = ? where id = ?", slot, id);
    }

    public void insertHealth(String id, String runId, String manifestId, BigDecimal score, boolean signatureOk,
                             boolean hashOk, boolean abiOk, boolean resourceOk) {
        jdbc.update("insert into plugin_health(id, run_id, manifest_id, score, signature_ok, hash_ok, abi_ok, resource_ok) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, runId, manifestId, score, signatureOk, hashOk, abiOk, resourceOk);
    }

    public void insertRollback(String id, String runId, String fromId, String toId, String reason, String actor) {
        jdbc.update("insert into plugin_rollback(id, run_id, from_id, to_id, reason, actor) values (?, ?, ?, ?, ?, ?)",
                id, runId, fromId, toId, reason, actor);
    }

    public List<Map<String, Object>> listManifests(String runId) {
        return jdbc.query("select id, run_id, name, version, arch, abi, resource_mb, payload_hash, signature, payload, "
                        + "slot, reason, health, loaded, hash_checked, check_order from plugin_manifest "
                        + "where run_id = ? order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = manifestRow(rs);
                    row.remove("signature");
                    row.remove("payload");
                    return row;
                }, runId);
    }

    public Map<String, Object> findManifest(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, name, version, arch, abi, resource_mb, "
                        + "payload_hash, signature, payload, slot, reason, health, loaded, hash_checked, check_order "
                        + "from plugin_manifest where id = ?",
                (rs, rowNum) -> manifestRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findSlot(String runId, String slot) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, name, version, arch, abi, resource_mb, "
                        + "payload_hash, signature, payload, slot, reason, health, loaded, hash_checked, check_order "
                        + "from plugin_manifest where run_id = ? and slot = ? order by id",
                (rs, rowNum) -> manifestRow(rs), runId, slot);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listHealth(String runId) {
        return jdbc.query("select id, run_id, manifest_id, score, signature_ok, hash_ok, abi_ok, resource_ok "
                        + "from plugin_health where run_id = ? order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("manifestId", rs.getString("manifest_id"));
                    row.put("score", rs.getBigDecimal("score"));
                    row.put("signatureOk", rs.getBoolean("signature_ok"));
                    row.put("hashOk", rs.getBoolean("hash_ok"));
                    row.put("abiOk", rs.getBoolean("abi_ok"));
                    row.put("resourceOk", rs.getBoolean("resource_ok"));
                    return row;
                }, runId);
    }

    public List<Map<String, Object>> listRollbacks(String runId) {
        return jdbc.query("select id, run_id, from_id, to_id, reason, actor from plugin_rollback "
                        + "where run_id = ? order by id",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("runId", rs.getString("run_id"));
                    row.put("fromId", rs.getString("from_id"));
                    row.put("toId", rs.getString("to_id"));
                    row.put("reason", rs.getString("reason"));
                    row.put("actor", rs.getString("actor"));
                    return row;
                }, runId);
    }

    private static Map<String, Object> manifestRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("name", rs.getString("name"));
        row.put("version", rs.getString("version"));
        row.put("arch", rs.getString("arch"));
        row.put("abi", rs.getString("abi"));
        row.put("resourceMb", Integer.valueOf(rs.getInt("resource_mb")));
        row.put("payloadHash", rs.getString("payload_hash"));
        row.put("signature", rs.getString("signature"));
        row.put("payload", rs.getString("payload"));
        row.put("slot", rs.getString("slot"));
        row.put("reason", rs.getString("reason"));
        row.put("health", rs.getBigDecimal("health"));
        row.put("loaded", rs.getBoolean("loaded"));
        row.put("hashChecked", rs.getBoolean("hash_checked"));
        row.put("checkOrder", rs.getString("check_order"));
        return row;
    }
}
