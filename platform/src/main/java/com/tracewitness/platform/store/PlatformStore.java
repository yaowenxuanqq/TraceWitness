package com.tracewitness.platform.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.id.EntityIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PlatformStore {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<List<String>>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlatformStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public RunManifest findManifest(String runId) {
        List<RunManifest> rows = jdbc.query("select run_id, scenario_id, clock_mode, epoch_start, tick_ms, tick, master_seed, mock_mode, created_at from run_manifest where run_id = ?", (rs, rowNum) -> new RunManifest(
                rs.getString("run_id"),
                rs.getString("scenario_id"),
                rs.getString("clock_mode"),
                rs.getString("epoch_start"),
                rs.getLong("tick_ms"),
                rs.getLong("tick"),
                rs.getLong("master_seed"),
                rs.getBoolean("mock_mode"),
                rs.getString("created_at")), runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertManifest(RunManifest manifest) {
        jdbc.update("insert into run_manifest( run_id, scenario_id, clock_mode, epoch_start, tick_ms, tick, master_seed, mock_mode, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", manifest.getRunId(), manifest.getScenarioId(), manifest.getClockMode(), manifest.getEpochStart(),
                manifest.getTickMs(), manifest.getTick(), manifest.getMasterSeed(), manifest.isMockMode(), manifest.getCreatedAt());
    }

    public void updateTick(String runId, long tick) {
        jdbc.update("update run_manifest set tick = ? where run_id = ?", tick, runId);
    }

    public String nextId(String runId, String entity) {
        Long current = jdbc.query(
                "select seq from id_sequence where run_id = ? and entity = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                runId,
                entity);
        long next = current == null ? 1 : current + 1;
        if (current == null) {
            jdbc.update("insert into id_sequence(run_id, entity, seq) values (?, ?, ?)", runId, entity, next);
        } else {
            jdbc.update("update id_sequence set seq = ? where run_id = ? and entity = ?", next, runId, entity);
        }
        return EntityIds.format(entity, runId, next);
    }

    public void insertSite(String id, String runId, String code, String name, BigDecimal xM, BigDecimal yM) {
        jdbc.update("insert into site_node(id, run_id, code, name, x_m, y_m) values (?, ?, ?, ?, ?, ?)",
                id, runId, code, name, xM, yM);
    }

    public void insertDevice(String id, String runId, String siteId, String code, String kind, String name) {
        jdbc.update("insert into device(id, run_id, site_id, code, kind, name) values (?, ?, ?, ?, ?, ?)",
                id, runId, siteId, code, kind, name);
    }

    public void insertEvent(String id, String runId, String deviceId, String siteId, String eventType,
                             String summary, String occurredAt, long tick) {
        jdbc.update("insert into event(id, run_id, device_id, site_id, event_type, summary, occurred_at, tick) values (?, ?, ?, ?, ?, ?, ?, ?)", id, runId, deviceId, siteId, eventType, summary, occurredAt, tick);
    }

    public long insertOutbox(String runId, String eventId, String payload, String createdAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("insert into outbox(run_id, event_id, payload, status, created_at) values (?, ?, ?, 'PENDING', ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, runId);
            statement.setString(2, eventId);
            statement.setString(3, payload);
            statement.setString(4, createdAt);
            return statement;
        }, keys);
        if (keys.getKeyList().isEmpty()) {
            throw new IllegalStateException("outbox id missing");
        }
        Object value = keys.getKeyList().get(0).getOrDefault("ID", keys.getKeyList().get(0).values().iterator().next());
        return ((Number) value).longValue();
    }

    public int publishIds(List<Long> ids, String publishedAt) {
        int published = 0;
        for (Long id : ids) {
            published += jdbc.update(
                    "update outbox set status = 'PUBLISHED', published_at = ? where id = ? and status = 'PENDING'",
                    publishedAt, id);
        }
        return published;
    }

    public int publishRemaining(String publishedAt) {
        return jdbc.update(
                "update outbox set status = 'PUBLISHED', published_at = ? where status = 'PENDING'",
                publishedAt);
    }

    public Map<String, Object> outboxCounts(String runId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("pending", countOutbox(runId, "PENDING"));
        counts.put("published", countOutbox(runId, "PUBLISHED"));
        return counts;
    }

    public List<Map<String, Object>> listSites(String runId) {
        return jdbc.query("select id, run_id, code, name, x_m, y_m from site_node where run_id = ? order by id", (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("runId", rs.getString("run_id"));
            row.put("code", rs.getString("code"));
            row.put("name", rs.getString("name"));
            row.put("xM", rs.getBigDecimal("x_m"));
            row.put("yM", rs.getBigDecimal("y_m"));
            return row;
        }, runId);
    }

    public List<Map<String, Object>> listDevices(String runId) {
        return jdbc.query("select id, run_id, site_id, code, kind, name from device where run_id = ? order by id", (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("runId", rs.getString("run_id"));
            row.put("siteId", rs.getString("site_id"));
            row.put("code", rs.getString("code"));
            row.put("kind", rs.getString("kind"));
            row.put("name", rs.getString("name"));
            return row;
        }, runId);
    }

    public List<Map<String, Object>> listEvents(String runId, int limit) {
        return jdbc.query("select id, run_id, device_id, site_id, event_type, summary, occurred_at, tick from event where run_id = ? order by tick, id limit ?", (rs, rowNum) -> eventRow(rs), runId, limit);
    }

    public Map<String, Object> findEvent(String id) {
        List<Map<String, Object>> rows = jdbc.query("select id, run_id, device_id, site_id, event_type, summary, occurred_at, tick from event where id = ?", (rs, rowNum) -> eventRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findAgentRunByRun(String runId) {
        List<Map<String, Object>> rows = queryAgent("where run_id = ?", runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findAgentRunById(String id) {
        List<Map<String, Object>> rows = queryAgent("where id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listAgentRuns() {
        return queryAgent("order by created_at, id");
    }

    public void insertAgentRun(String id, String runId, String task, String status, boolean mockMode,
                                String conclusion, boolean insufficient, BigDecimal confidence,
                                String sourceIds, String failureType, String requestedActions, String createdAt) {
        jdbc.update("insert into agent_run( id, run_id, task, status, mock_mode, conclusion, insufficient_evidence, confidence, source_ids, failure_type, requested_actions, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, runId, task, status, mockMode, conclusion, insufficient, confidence,
                sourceIds, failureType, requestedActions, createdAt);
    }

    public void insertStep(String id, String agentRunId, String runId, String stepKind, String detail, String createdAt) {
        jdbc.update("insert into agent_step(id, agent_run_id, run_id, step_kind, detail, created_at) values (?, ?, ?, ?, ?, ?)", id, agentRunId, runId, stepKind, detail, createdAt);
    }

    public void insertToolCall(String id, String agentRunId, String runId, String toolName,
                                String argumentsJson, String resultJson, String sourceIds, String createdAt) {
        jdbc.update("insert into tool_call( id, agent_run_id, run_id, tool_name, arguments_json, result_json, source_ids, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)", id, agentRunId, runId, toolName, argumentsJson, resultJson, sourceIds, createdAt);
    }

    public List<Map<String, Object>> listSteps(String agentRunId) {
        return jdbc.query("select id, agent_run_id, run_id, step_kind, detail, created_at from agent_step where agent_run_id = ? order by id", (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("agentRunId", rs.getString("agent_run_id"));
            row.put("runId", rs.getString("run_id"));
            row.put("stepKind", rs.getString("step_kind"));
            row.put("detail", rs.getString("detail"));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, agentRunId);
    }

    public List<Map<String, Object>> listToolCalls(String agentRunId) {
        return jdbc.query("select id, agent_run_id, run_id, tool_name, arguments_json, result_json, source_ids, created_at from tool_call where agent_run_id = ? order by id", (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("agentRunId", rs.getString("agent_run_id"));
            row.put("runId", rs.getString("run_id"));
            row.put("toolName", rs.getString("tool_name"));
            row.put("arguments", readJson(rs.getString("arguments_json")));
            row.put("result", readJson(rs.getString("result_json")));
            row.put("sourceIds", readList(rs.getString("source_ids")));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, agentRunId);
    }

    public String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int countOutbox(String runId, String status) {
        Integer count = jdbc.queryForObject(
                "select count(*) from outbox where run_id = ? and status = ?",
                Integer.class,
                runId,
                status);
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> queryAgent(String suffix, Object... args) {
        return jdbc.query("select id, run_id, task, status, mock_mode, conclusion, insufficient_evidence, confidence, source_ids, failure_type, requested_actions, created_at from agent_run " + suffix, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("runId", rs.getString("run_id"));
            row.put("task", rs.getString("task"));
            row.put("status", rs.getString("status"));
            row.put("mockMode", rs.getBoolean("mock_mode"));
            row.put("conclusion", rs.getString("conclusion"));
            row.put("insufficientEvidence", rs.getBoolean("insufficient_evidence"));
            row.put("confidence", rs.getBigDecimal("confidence"));
            row.put("sourceIds", readList(rs.getString("source_ids")));
            row.put("failureType", rs.getString("failure_type"));
            row.put("requestedActions", readList(rs.getString("requested_actions")));
            row.put("createdAt", rs.getString("created_at"));
            return row;
        }, args);
    }

    private Map<String, Object> eventRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("runId", rs.getString("run_id"));
        row.put("deviceId", rs.getString("device_id"));
        row.put("siteId", rs.getString("site_id"));
        row.put("eventType", rs.getString("event_type"));
        row.put("summary", rs.getString("summary"));
        row.put("occurredAt", rs.getString("occurred_at"));
        row.put("tick", rs.getLong("tick"));
        return row;
    }

    private List<String> readList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            return mapper.readValue(json, STRINGS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Object readJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static final class RunManifest {
        private final String runId;
        private final String scenarioId;
        private final String clockMode;
        private final String epochStart;
        private final long tickMs;
        private final long tick;
        private final long masterSeed;
        private final boolean mockMode;
        private final String createdAt;

        public RunManifest(String runId, String scenarioId, String clockMode, String epochStart,
                           long tickMs, long tick, long masterSeed, boolean mockMode, String createdAt) {
            this.runId = runId;
            this.scenarioId = scenarioId;
            this.clockMode = clockMode;
            this.epochStart = epochStart;
            this.tickMs = tickMs;
            this.tick = tick;
            this.masterSeed = masterSeed;
            this.mockMode = mockMode;
            this.createdAt = createdAt;
        }

        public String getRunId() { return runId; }
        public String getScenarioId() { return scenarioId; }
        public String getClockMode() { return clockMode; }
        public String getEpochStart() { return epochStart; }
        public long getTickMs() { return tickMs; }
        public long getTick() { return tick; }
        public long getMasterSeed() { return masterSeed; }
        public boolean isMockMode() { return mockMode; }
        public String getCreatedAt() { return createdAt; }
    }
}
