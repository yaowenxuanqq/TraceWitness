package com.tracewitness.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.tracewitness.platform.agent.InvestigationTools;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.ProfileStore;
import com.tracewitness.platform.store.TrajectoryStore;
import com.tracewitness.platform.trajectory.TrajectoryPolicy;
import com.tracewitness.platform.trajectory.TrajectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryWeek10Test {
    private static final String RUN_ID = "run-w02-mock-incident-20261110";

    @Test
    void unreachablePointsDropAndEvidenceVerifies() throws Exception {
        Path dir = Files.createTempDirectory("tw-w10");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        HttpServer server = stub();
        server.start();
        try {
            String[] properties = properties(database, dir.resolve("media"), server.getAddress().getPort());
            Map<String, Object> first = read(properties);
            Map<String, Object> second = read(properties);
            assertEquals(first.get("trajectoryIds"), second.get("trajectoryIds"));
            assertEquals(first.get("packageIds"), second.get("packageIds"));
            assertEquals(Boolean.FALSE, first.get("roofKept"));
            assertEquals("unreachable", first.get("roofReason"));
            assertEquals("travel_conflict", first.get("fastReason"));
            assertEquals(Integer.valueOf(2), first.get("thinCount"));
            assertEquals(Integer.valueOf(30), first.get("gap"));
            assertEquals("lobby", first.get("companionDoor"));
            assertEquals(Boolean.TRUE, first.get("digestMatches"));
            assertEquals("evidence_missing", first.get("missingStatus"));
            assertEquals("cloud-script", first.get("winner"));
            assertEquals(Boolean.FALSE, first.get("trained"));
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
                TrajectoryService service = context.getBean(TrajectoryService.class);
                TrajectoryStore store = context.getBean(TrajectoryStore.class);
                InvestigationTools tools = context.getBean(InvestigationTools.class);
                Map<String, Object> clean = service.verify(String.valueOf(first.get("packageId")), null);
                assertEquals(Boolean.TRUE, clean.get("valid"));
                Map<String, Object> tampered = service.verify(String.valueOf(first.get("packageId")), "tampered\n");
                assertEquals(Boolean.FALSE, tampered.get("valid"));
                Map<String, Object> missing = service.verify(String.valueOf(first.get("missingId")), null);
                assertEquals(Boolean.FALSE, missing.get("valid"));
                assertEquals("evidence_missing", missing.get("error"));
                assertTrue(service.exportHardCases(RUN_ID).isEmpty());
                Map<String, Object> labeled = service.label(String.valueOf(first.get("hardId")), "true_negative", "investigator");
                assertEquals(Boolean.TRUE, labeled.get("applied"));
                List<Map<String, Object>> exported = service.exportHardCases(RUN_ID);
                assertEquals(1, exported.size());
                assertEquals("roof-point", exported.get(0).get("id"));
                assertEquals("true_negative", exported.get(0).get("label"));
                assertEquals(Boolean.FALSE, service.label(String.valueOf(first.get("hardId")), "true_negative", "investigator").get("applied"));
                boolean audited = false;
                for (Map<String, Object> audit : context.getBean(ProfileStore.class).listAudits(RUN_ID)) {
                    if ("hard_labeled".equals(audit.get("action")) && first.get("hardId").equals(audit.get("targetId"))) {
                        audited = true;
                    }
                }
                assertTrue(audited);
                Map<String, Object> companions = tools.execute(RUN_ID, "find_companions", new LinkedHashMap<String, Object>());
                assertEquals(store.listCompanions(RUN_ID).get(0).get("id"), ((List<?>) companions.get("source_ids")).get(0));
                Map<String, Object> args = new LinkedHashMap<String, Object>();
                args.put("profile_id", "resident-chen");
                Map<String, Object> trail = tools.execute(RUN_ID, "build_trajectory", args);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) trail.get("trajectories");
                assertEquals(1, rows.size());
                assertFalse(((List<?>) rows.get(0).get("keptDoors")).contains("roof"));
                assertTrue(((List<?>) rows.get(0).get("droppedDoors")).contains("roof"));
                Map<String, Object> exportArgs = new LinkedHashMap<String, Object>();
                List<String> sources = new ArrayList<String>();
                sources.add(String.valueOf(context.getBean(PlatformStore.class).listEvents(RUN_ID, 1).get(0).get("id")));
                exportArgs.put("source_ids", sources);
                Map<String, Object> exportedEvidence = tools.execute(RUN_ID, "export_evidence", exportArgs);
                assertEquals(null, exportedEvidence.get("error"));
                assertTrue(String.valueOf(exportedEvidence.get("digest")).length() == 64);
                Map<String, Object> absent = new LinkedHashMap<String, Object>();
                absent.put("source_ids", java.util.Collections.singletonList("missing-event"));
                assertEquals("evidence_missing", tools.execute(RUN_ID, "export_evidence", absent).get("error"));
            }
        } finally {
            server.stop(0);
        }
    }

    private static Map<String, Object> read(String[] properties) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
            TrajectoryStore store = context.getBean(TrajectoryStore.class);
            Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
            List<String> trajectoryIds = new ArrayList<String>();
            for (Map<String, Object> trajectory : store.listTrajectories(RUN_ID)) {
                trajectoryIds.add(String.valueOf(trajectory.get("id")));
                List<Map<String, Object>> points = store.listPoints(String.valueOf(trajectory.get("id")));
                if ("chen-walk".equals(trajectory.get("caseKey"))) {
                    boolean roofKept = false;
                    String roofReason = null;
                    for (Map<String, Object> point : points) {
                        if ("roof".equals(point.get("doorCode"))) {
                            roofKept = Boolean.TRUE.equals(point.get("kept"));
                            roofReason = String.valueOf(point.get("reason"));
                        }
                    }
                    snapshot.put("roofKept", Boolean.valueOf(roofKept));
                    snapshot.put("roofReason", roofReason);
                }
                if ("fast-unit".equals(trajectory.get("caseKey"))) {
                    for (Map<String, Object> point : points) {
                        if ("unit-b".equals(point.get("doorCode"))) {
                            snapshot.put("fastReason", point.get("reason"));
                        }
                    }
                }
                if ("dwell-thin".equals(trajectory.get("caseKey"))) {
                    snapshot.put("thinCount", trajectory.get("thinCount"));
                    int thinned = 0;
                    for (Map<String, Object> point : points) {
                        if (Boolean.TRUE.equals(point.get("thinned"))) {
                            thinned++;
                        }
                    }
                    assertEquals(2, thinned);
                }
            }
            List<Map<String, Object>> companions = store.listCompanions(RUN_ID);
            assertEquals(1, companions.size());
            snapshot.put("gap", companions.get(0).get("gapSeconds"));
            snapshot.put("companionDoor", companions.get(0).get("doorCode"));
            List<String> packageIds = new ArrayList<String>();
            for (Map<String, Object> packed : store.listPackages(RUN_ID)) {
                packageIds.add(String.valueOf(packed.get("id")));
                if ("chen-walk".equals(packed.get("caseKey"))) {
                    snapshot.put("packageId", packed.get("id"));
                    snapshot.put("digestMatches", Boolean.valueOf(String.valueOf(packed.get("digest"))
                            .equals(TrajectoryPolicy.sha256(String.valueOf(packed.get("manifest"))))));
                    assertEquals("valid", packed.get("status"));
                }
                if ("missing-clip".equals(packed.get("caseKey"))) {
                    snapshot.put("missingId", packed.get("id"));
                    snapshot.put("missingStatus", packed.get("status"));
                    assertEquals(null, packed.get("digest"));
                }
            }
            Map<String, Object> hardCase = store.listHardCases(RUN_ID).get(0);
            snapshot.put("hardId", hardCase.get("id"));
            assertEquals("open", hardCase.get("status"));
            Map<String, Object> compare = store.listCompares(RUN_ID).get(0);
            snapshot.put("winner", compare.get("winner"));
            snapshot.put("trained", compare.get("trained"));
            snapshot.put("trajectoryIds", trajectoryIds);
            snapshot.put("packageIds", packageIds);
            assertEquals(4, trajectoryIds.size());
            return snapshot;
        }
    }

    private static HttpServer stub() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/agent/select-tool", exchange -> {
            exchange.getRequestBody().close();
            ObjectNode response = mapper.createObjectNode();
            response.putArray("tool_calls");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/agent/report", exchange -> {
            exchange.getRequestBody().close();
            ObjectNode response = mapper.createObjectNode();
            response.put("conclusion", "证据不足");
            response.putArray("source_ids");
            response.put("confidence", 0);
            response.put("insufficient_evidence", true);
            response.putArray("requested_actions");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/vision/track", exchange -> {
            exchange.getRequestBody().close();
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
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/vision/structure", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            String caseKey = body.path("case_key").asText("live");
            ObjectNode response = mapper.createObjectNode();
            response.put("attribute_calls", 1);
            response.put("face_quality", 0.90);
            response.put("body_quality", 0.90);
            response.put("mode", "track_break".equals(caseKey) ? "track_break" : "gate_failure".equals(caseKey) ? "gate_failure" : "preferred");
            response.put("frame_kind", "gate_failure".equals(caseKey) ? "full_frame" : "crop");
            response.put("chosen_frame", 0);
            response.put("chosen_tick", 0);
            response.putObject("attributes").put("upper_color", "light").put("posture", "upright");
            response.putObject("masked").put("upper_color", "light").put("subject_token", "masked").put("face_region", "redacted");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        return server;
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

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
