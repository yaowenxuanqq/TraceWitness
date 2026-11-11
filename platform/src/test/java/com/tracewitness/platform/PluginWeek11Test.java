package com.tracewitness.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.tracewitness.platform.plugin.PluginPolicy;
import com.tracewitness.platform.plugin.PluginService;
import com.tracewitness.platform.store.PluginStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginWeek11Test {
    private static final String RUN_ID = "run-w02-mock-incident-20261110";

    @Test
    void badSignatureDoesNotLoadAndFaultRollsBack() throws Exception {
        Path dir = Files.createTempDirectory("tw-w11");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        HttpServer server = stub();
        server.start();
        try {
            String[] properties = properties(database, dir.resolve("media"), server.getAddress().getPort());
            Map<String, Object> first = read(properties);
            Map<String, Object> second = read(properties);
            assertEquals(first.get("ids"), second.get("ids"));
            assertEquals("1.1.0", first.get("activeVersion"));
            assertEquals("1.0.0", first.get("previousVersion"));
            assertEquals(Boolean.FALSE, first.get("forgedLoaded"));
            assertEquals(Boolean.FALSE, first.get("forgedHashChecked"));
            assertEquals("rejected", first.get("forgedSlot"));
            assertEquals(first.get("forgedHash"), PluginPolicy.sha256("entry=forged\n"));
            assertEquals("candidate", first.get("crowdSlot"));
            assertEquals("0.55", String.valueOf(first.get("crowdHealth")));
            assertEquals(0, first.get("rollbacks"));
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
                PluginService service = context.getBean(PluginService.class);
                PluginStore store = context.getBean(PluginStore.class);
                Map<String, Object> rolled = service.rollback("investigator");
                assertEquals(Boolean.TRUE, rolled.get("applied"));
                assertEquals("1.0.0", version(store, "active"));
                assertEquals("1.1.0", version(store, "previous"));
                Map<String, Object> restored = service.rollback("investigator");
                assertEquals(Boolean.TRUE, restored.get("applied"));
                assertEquals("1.1.0", version(store, "active"));
                Map<String, Object> active = store.findSlot(RUN_ID, "active");
                Map<String, Object> fault = service.fault(String.valueOf(active.get("id")));
                assertEquals(Boolean.TRUE, fault.get("applied"));
                assertEquals(Boolean.FALSE, fault.get("loaded"));
                assertEquals(Boolean.FALSE, fault.get("hashChecked"));
                assertEquals("bad_signature", fault.get("reason"));
                assertEquals("1.0.0", version(store, "active"));
                List<Map<String, Object>> rollbacks = store.listRollbacks(RUN_ID);
                assertEquals(3, rollbacks.size());
                assertEquals("manual", rollbacks.get(0).get("reason"));
                assertEquals("fault", rollbacks.get(2).get("reason"));
                assertNotNull(rollbacks.get(2).get("toId"));
                Map<String, Object> forged = byVersion(store.listManifests(RUN_ID), "1.2.0");
                assertFalse(Boolean.TRUE.equals(forged.get("loaded")));
                assertEquals(Boolean.FALSE, service.fault(String.valueOf(forged.get("id"))).get("applied"));
            }
        } finally {
            server.stop(0);
        }
    }

    private static Map<String, Object> read(String[] properties) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
            PluginStore store = context.getBean(PluginStore.class);
            List<String> ids = new ArrayList<String>();
            Map<String, Object> snapshot = new java.util.LinkedHashMap<String, Object>();
            for (Map<String, Object> item : store.listManifests(RUN_ID)) {
                ids.add(String.valueOf(item.get("id")));
                if ("1.2.0".equals(item.get("version"))) {
                    snapshot.put("forgedLoaded", item.get("loaded"));
                    snapshot.put("forgedHashChecked", item.get("hashChecked"));
                    snapshot.put("forgedSlot", item.get("slot"));
                    snapshot.put("forgedHash", item.get("payloadHash"));
                    assertEquals(PluginPolicy.CHECK_ORDER, item.get("checkOrder"));
                }
                if ("crowd-count".equals(item.get("name"))) {
                    snapshot.put("crowdSlot", item.get("slot"));
                    snapshot.put("crowdHealth", item.get("health"));
                    assertEquals(Boolean.FALSE, item.get("loaded"));
                }
            }
            snapshot.put("ids", ids);
            snapshot.put("activeVersion", version(store, "active"));
            snapshot.put("previousVersion", version(store, "previous"));
            snapshot.put("rollbacks", Integer.valueOf(store.listRollbacks(RUN_ID).size()));
            assertEquals(4, ids.size());
            return snapshot;
        }
    }

    private static String version(PluginStore store, String slot) {
        Map<String, Object> row = store.findSlot(RUN_ID, slot);
        return row == null ? null : String.valueOf(row.get("version"));
    }

    private static Map<String, Object> byVersion(List<Map<String, Object>> rows, String version) {
        for (Map<String, Object> row : rows) {
            if (version.equals(row.get("version"))) {
                return row;
            }
        }
        return null;
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
            response.put("case_key", caseKey);
            if ("gate_failure".equals(caseKey)) {
                response.put("mode", "gate_failure");
                response.put("frame_kind", "full_frame");
            } else if ("track_break".equals(caseKey)) {
                response.put("mode", "track_break");
                response.put("frame_kind", "crop");
            } else {
                response.put("mode", "preferred");
                response.put("frame_kind", "crop");
            }
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
