package com.tracewitness.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.tracewitness.platform.media.MediaService;
import com.tracewitness.platform.route.RouteService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.TrackStore;
import com.tracewitness.platform.vision.StructureService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartAcceptanceTest {
    private static final String RUN_ID = "run-w02-mock-incident-20261110";

    @Test
    void mockLoopRemainsReadableAfterRestart() throws Exception {
        Path dir = Files.createTempDirectory("tw-w02");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/agent/select-tool", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            if (!body.hasNonNull("request_timestamp")) {
                respond(exchange, 400, "{\"error\":\"clock\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            ObjectNode response = mapper.createObjectNode();
            response.put("tool_name", "search_events");
            response.putObject("arguments").put("limit", 5);
            respond(exchange, 200, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/vision/track", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            if (!body.hasNonNull("request_timestamp")) {
                respond(exchange, 400, "{\"error\":\"clock\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            ObjectNode response = mapper.createObjectNode();
            ObjectNode track = response.putArray("tracks").addObject();
            track.put("local_id", "track-1");
            ArrayNode points = track.putArray("points");
            for (int i = 0; i < 4; i++) {
                ObjectNode point = points.addObject();
                point.put("frame_index", i);
                point.put("tick", i);
                point.put("x", 0.2 + i * 0.1);
                point.put("y", 0.5);
                point.put("w", 0.08);
                point.put("h", 0.2);
                point.put("confidence", 0.9);
            }
            response.putArray("faults");
            respond(exchange, 200, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/vision/structure", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            if (!body.hasNonNull("request_timestamp")) {
                respond(exchange, 400, "{\"error\":\"clock\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String caseKey = body.path("case_key").asText("live");
            ObjectNode response = mapper.createObjectNode();
            response.put("case_key", caseKey);
            response.put("attribute_calls", 1);
            response.put("face_quality", 0.90);
            response.put("body_quality", 0.90);
            if ("gate_failure".equals(caseKey)) {
                response.put("mode", "gate_failure");
                response.put("frame_kind", "full_frame");
                response.put("chosen_frame", 0);
                response.put("chosen_tick", 0);
            } else if ("track_break".equals(caseKey)) {
                response.put("mode", "track_break");
                response.put("frame_kind", "crop");
                response.put("chosen_frame", 0);
                response.put("chosen_tick", 0);
            } else {
                response.put("mode", "preferred");
                response.put("frame_kind", "crop");
                response.put("chosen_frame", 0);
                response.put("chosen_tick", 0);
            }
            response.putObject("attributes").put("upper_color", "light").put("posture", "upright");
            ObjectNode masked = response.putObject("masked");
            masked.put("upper_color", "light");
            masked.put("subject_token", "masked");
            masked.put("face_region", "redacted");
            respond(exchange, 200, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/agent/report", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            ObjectNode response = mapper.createObjectNode();
            response.put("conclusion", "北门摄像机记录到人员进入、停留后离开。");
            ArrayNode ids = response.putArray("source_ids");
            body.path("tool_results").forEach(result -> result.path("source_ids").forEach(ids::add));
            response.put("confidence", 0.8);
            response.put("insufficient_evidence", false);
            response.putArray("requested_actions");
            respond(exchange, 200, mapper.writeValueAsBytes(response));
        });
        server.start();
        try {
            String[] properties = properties(database, dir.resolve("media"), server.getAddress().getPort());
            Map<String, Object> first = read(properties);
            Map<String, Object> second = read(properties);
            assertEquals(first.get("agentId"), second.get("agentId"));
            assertEquals(first.get("eventIds"), second.get("eventIds"));
            assertEquals(Arrays.asList(
                    "tw.event.run-w02-mock-incident-20261110.000001",
                    "tw.event.run-w02-mock-incident-20261110.000002",
                    "tw.event.run-w02-mock-incident-20261110.000003"), second.get("eventIds"));
            assertEquals("tw.tool.run-w02-mock-incident-20261110.000001", second.get("toolId"));
            assertEquals("MOCK_MODE", second.get("banner"));
            assertEquals(0, new BigDecimal("12.35").compareTo((BigDecimal) second.get("xM")));
            assertEquals("2026-01-01T00:00:01.000Z", second.get("firstAt"));
            assertEquals(3, second.get("published"));
            assertEquals(1, second.get("agentCount"));
            assertEquals(first.get("clipIds"), second.get("clipIds"));
            assertEquals(3, ((java.util.List<?>) second.get("clipIds")).size());
            assertEquals(first.get("trackId"), second.get("trackId"));
            assertEquals(4, second.get("pointCount"));
            assertEquals(first.get("observationIds"), second.get("observationIds"));
            assertEquals(first.get("decisionIds"), second.get("decisionIds"));
            assertEquals(Arrays.asList(
                    "tw.decision.run-w02-mock-incident-20261110.000001",
                    "tw.decision.run-w02-mock-incident-20261110.000002",
                    "tw.decision.run-w02-mock-incident-20261110.000003",
                    "tw.decision.run-w02-mock-incident-20261110.000004"), second.get("decisionIds"));
            assertEquals(Arrays.asList(
                    "tw.observation.run-w02-mock-incident-20261110.000001",
                    "tw.observation.run-w02-mock-incident-20261110.000002",
                    "tw.observation.run-w02-mock-incident-20261110.000003"), second.get("observationIds"));
        } finally {
            server.stop(0);
        }
    }

    private static Map<String, Object> read(String[] properties) throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class)
                .run(properties)) {
            PlatformStore store = context.getBean(PlatformStore.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Map<String, Object> agent = store.findAgentRunByRun(RUN_ID);
            List<Map<String, Object>> events = store.listEvents(RUN_ID, 20);
            List<Map<String, Object>> calls = store.listToolCalls(String.valueOf(agent.get("id")));
            Integer agentCount = jdbc.queryForObject("select count(*) from agent_run", Integer.class);
            Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            snapshot.put("agentId", agent.get("id"));
            snapshot.put("eventIds", events.stream().map(row -> row.get("id")).collect(Collectors.toList()));
            snapshot.put("toolId", calls.get(0).get("id"));
            snapshot.put("banner", context.getBean(com.tracewitness.platform.config.AppProperties.class).banner());
            snapshot.put("xM", store.listSites(RUN_ID).get(0).get("xM"));
            snapshot.put("firstAt", events.get(0).get("occurredAt"));
            snapshot.put("published", store.outboxCounts(RUN_ID).get("published"));
            snapshot.put("agentCount", agentCount);
            MediaService media = context.getBean(MediaService.class);
            Map<String, Object> timeline = media.timeline();
            assertEquals(Boolean.TRUE, timeline.get("audioContinuous"));
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> clips = (java.util.List<Map<String, Object>>) timeline.get("clips");
            assertEquals(3, clips.size());
            assertEquals(0L, ((Number) clips.get(0).get("startTick")).longValue());
            assertEquals(2L, ((Number) clips.get(0).get("endTick")).longValue());
            assertEquals(Boolean.TRUE, clips.get(0).get("audioContinuous"));
            byte[] wav = media.readStored(media.findAsset(String.valueOf(clips.get(0).get("id"))));
            assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
            assertEquals("WAVE", new String(wav, 8, 4, StandardCharsets.US_ASCII));
            snapshot.put("clipIds", clips.stream().map(row -> row.get("id")).collect(Collectors.toList()));
            TrackStore tracks = context.getBean(TrackStore.class);
            java.util.List<Map<String, Object>> trackRows = tracks.listTracks(RUN_ID);
            assertEquals(1, trackRows.size());
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> points = (java.util.List<Map<String, Object>>) trackRows.get(0).get("points");
            assertEquals(4, points.size());
            assertTrue(((BigDecimal) points.get(3).get("x")).compareTo((BigDecimal) points.get(0).get("x")) > 0);
            snapshot.put("trackId", trackRows.get(0).get("id"));
            snapshot.put("pointCount", points.size());
            StructureService structure = context.getBean(StructureService.class);
            java.util.List<Map<String, Object>> observations = structure.list();
            assertEquals(3, observations.size());
            assertEquals("preferred", observations.get(0).get("mode"));
            assertEquals("crop", observations.get(0).get("frameKind"));
            assertEquals(1, observations.get(0).get("attributeCalls"));
            assertEquals("gate_failure", observations.get(1).get("mode"));
            assertEquals("full_frame", observations.get(1).get("frameKind"));
            assertEquals(0, observations.get(1).get("chosenFrame"));
            assertEquals("track_break", observations.get(2).get("mode"));
            assertEquals(0, observations.get(2).get("chosenFrame"));
            assertEquals("redacted", ((Map<?, ?>) observations.get(0).get("masked")).get("face_region"));
            byte[] thumb = structure.readThumbnail(String.valueOf(observations.get(0).get("id")));
            assertEquals("PNG", new String(thumb, 1, 3, StandardCharsets.US_ASCII));
            snapshot.put("observationIds", observations.stream().map(row -> row.get("id")).collect(Collectors.toList()));
            RouteService routes = context.getBean(RouteService.class);
            java.util.List<Map<String, Object>> decisions = routes.list();
            assertEquals(4, decisions.size());
            assertEquals("live", decisions.get(0).get("caseKey"));
            assertEquals("local", decisions.get(0).get("route"));
            assertEquals(Boolean.FALSE, decisions.get(0).get("degraded"));
            assertEquals(Boolean.FALSE, decisions.get(0).get("cloudCalled"));
            assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) decisions.get(0).get("qInput")));
            assertEquals(0, new BigDecimal("0.50").compareTo((BigDecimal) decisions.get(0).get("qObserved")));
            assertEquals("gate_failure", decisions.get(1).get("caseKey"));
            assertEquals(Boolean.TRUE, decisions.get(1).get("degraded"));
            assertEquals("history_short", decisions.get(1).get("reason"));
            assertEquals(Boolean.FALSE, decisions.get(1).get("cloudCalled"));
            assertEquals("track_break", decisions.get(2).get("caseKey"));
            assertEquals("cloud", decisions.get(2).get("route"));
            assertEquals("conflict", decisions.get(2).get("fusion"));
            assertEquals(Boolean.TRUE, decisions.get(2).get("cloudCalled"));
            assertEquals("unknown_business", decisions.get(3).get("caseKey"));
            assertEquals("blocked", decisions.get(3).get("route"));
            assertEquals(Boolean.FALSE, decisions.get(3).get("cloudCalled"));
            routes.verifyReplay();
            snapshot.put("decisionIds", decisions.stream().map(row -> row.get("id")).collect(Collectors.toList()));
            assertTrue(Boolean.TRUE.equals(agent.get("mockMode")));
            assertEquals("search_events", calls.get(0).get("toolName"));
            return snapshot;
        }
    }

    private static String[] properties(String database, Path media, int port) {
        return new String[]{
                "--spring.datasource.url=jdbc:h2:file:" + database,
                "--spring.datasource.username=sa",
                "--tracewitness.mode=MOCK",
                "--tracewitness.auto-loop=true",
                "--tracewitness.ai-base-url=http://127.0.0.1:" + port,
                "--tracewitness.media-dir=" + media.toAbsolutePath().toString().replace('\\', '/'),
                "--server.port=0"
        };
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
