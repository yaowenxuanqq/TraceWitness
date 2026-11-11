package com.tracewitness.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.tracewitness.platform.retrieval.RetrievalService;
import com.tracewitness.platform.store.ProfileStore;
import com.tracewitness.platform.store.RetrievalStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalWeek9Test {
    private static final String RUN_ID = "run-w02-mock-incident-20261110";

    @Test
    void searchExplainsHitsAndOutboundDoesNotAlert() throws Exception {
        Path dir = Files.createTempDirectory("tw-w09");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        HttpServer server = stub();
        server.start();
        try {
            String[] properties = properties(database, dir.resolve("media"), server.getAddress().getPort());
            Map<String, Object> first = read(properties);
            Map<String, Object> second = read(properties);
            assertEquals(first.get("queryIds"), second.get("queryIds"));
            assertEquals(first.get("passageIds"), second.get("passageIds"));
            assertEquals("resident-chen", first.get("top"));
            assertTrue(String.valueOf(first.get("explanation")).contains("face"));
            assertEquals("hard_attribute", first.get("hardReason"));
            assertEquals(Boolean.FALSE, first.get("residentHit"));
            assertEquals(Boolean.FALSE, first.get("outAlert"));
            assertEquals(Boolean.TRUE, first.get("inAlert"));
            assertEquals(Boolean.FALSE, first.get("inExecuted"));
            assertEquals("pending_human", first.get("inAction"));
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
                RetrievalService retrieval = context.getBean(RetrievalService.class);
                RetrievalStore store = context.getBean(RetrievalStore.class);
                Map<String, Object> decided = retrieval.decideAlert(String.valueOf(first.get("inId")), "approve", "investigator");
                assertEquals(Boolean.TRUE, decided.get("applied"));
                Map<String, Object> passage = store.findPassage(String.valueOf(first.get("inId")));
                assertEquals("confirmed", passage.get("status"));
                assertEquals(Boolean.FALSE, passage.get("executed"));
                assertEquals("pending_human", passage.get("actionStatus"));
                boolean audited = false;
                for (Map<String, Object> audit : context.getBean(ProfileStore.class).listAudits(RUN_ID)) {
                    if ("alert_confirmed".equals(audit.get("action")) && first.get("inId").equals(audit.get("targetId"))) {
                        audited = true;
                    }
                }
                assertTrue(audited);
                assertEquals(Boolean.FALSE, retrieval.decideAlert(String.valueOf(first.get("inId")), "approve", "investigator").get("applied"));
            }
        } finally {
            server.stop(0);
        }
    }

    private static Map<String, Object> read(String[] properties) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
            RetrievalStore store = context.getBean(RetrievalStore.class);
            Map<String, Object> snapshot = new java.util.LinkedHashMap<String, Object>();
            List<String> queryIds = new java.util.ArrayList<String>();
            for (Map<String, Object> query : store.listQueries(RUN_ID)) {
                queryIds.add(String.valueOf(query.get("id")));
                List<Map<String, Object>> hits = store.listHits(String.valueOf(query.get("id")));
                List<Map<String, Object>> exclusions = store.listExclusions(String.valueOf(query.get("id")));
                if ("resident-multi".equals(query.get("caseKey"))) {
                    snapshot.put("top", hits.get(0).get("candidateCode"));
                    snapshot.put("explanation", hits.get(0).get("explanation"));
                    assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) hits.get(0).get("faceScore")));
                    assertTrue(String.valueOf(hits.get(0).get("explanation")).startsWith("hard pass"));
                }
                if ("hard-dark".equals(query.get("caseKey"))) {
                    boolean residentHit = false;
                    for (Map<String, Object> hit : hits) {
                        if ("resident-chen".equals(hit.get("candidateCode"))) {
                            residentHit = true;
                        }
                    }
                    snapshot.put("residentHit", Boolean.valueOf(residentHit));
                    String reason = null;
                    for (Map<String, Object> exclusion : exclusions) {
                        if ("resident-chen".equals(exclusion.get("candidateCode"))) {
                            reason = String.valueOf(exclusion.get("reason"));
                        }
                    }
                    snapshot.put("hardReason", reason);
                }
            }
            List<String> passageIds = new java.util.ArrayList<String>();
            for (Map<String, Object> passage : store.listPassages(RUN_ID)) {
                passageIds.add(String.valueOf(passage.get("id")));
                if ("bike-out".equals(passage.get("caseKey"))) {
                    snapshot.put("outAlert", passage.get("alerted"));
                    assertEquals(Integer.valueOf(0), passage.get("streak"));
                }
                if ("bike-in".equals(passage.get("caseKey"))) {
                    snapshot.put("inAlert", passage.get("alerted"));
                    snapshot.put("inExecuted", passage.get("executed"));
                    snapshot.put("inAction", passage.get("actionStatus"));
                    snapshot.put("inId", passage.get("id"));
                    assertEquals("pending_human", passage.get("status"));
                }
                if ("bike-gap".equals(passage.get("caseKey"))) {
                    assertFalse(Boolean.TRUE.equals(passage.get("alerted")));
                    assertEquals(Integer.valueOf(2), passage.get("streak"));
                }
            }
            snapshot.put("queryIds", queryIds);
            snapshot.put("passageIds", passageIds);
            assertEquals(3, queryIds.size());
            assertEquals(4, passageIds.size());
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
