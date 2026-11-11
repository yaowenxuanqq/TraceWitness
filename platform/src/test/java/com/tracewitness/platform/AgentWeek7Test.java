package com.tracewitness.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.tracewitness.platform.store.PlatformStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWeek7Test {
    @Test
    void citedReportRetriesOnceThenKeepsToolIds() throws Exception {
        Path dir = Files.createTempDirectory("tw-w07");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/agent/select-tool", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            ObjectNode response = mapper.createObjectNode();
            ArrayNode calls = response.putArray("tool_calls");
            ArrayNode prior = (ArrayNode) body.path("tool_results");
            boolean detailed = false;
            for (int i = 0; i < prior.size(); i++) {
                if ("get_event_detail".equals(prior.get(i).path("tool_name").asText())) {
                    detailed = true;
                }
            }
            if (prior.size() == 0) {
                calls.addObject().put("tool_name", "search_events").putObject("arguments").put("limit", 5);
            } else if (!detailed) {
                String eventId = prior.get(0).path("source_ids").get(0).asText();
                calls.addObject().put("tool_name", "get_event_detail").putObject("arguments").put("event_id", eventId);
            }
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/agent/report", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            ObjectNode response = mapper.createObjectNode();
            ArrayNode ids = response.putArray("source_ids");
            if (!body.hasNonNull("citation_hint")) {
                ids.add("tw.event.other.000001");
                response.put("conclusion", "非法引用");
            } else {
                body.path("tool_results").forEach(result -> result.path("source_ids").forEach(ids::add));
                response.put("conclusion", "北门摄像机记录到人员进入、停留后离开。");
            }
            response.put("confidence", 0.8);
            response.put("insufficient_evidence", false);
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
            response.put("case_key", caseKey);
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
        server.start();
        try {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(
                    "--spring.datasource.url=jdbc:h2:file:" + database,
                    "--spring.datasource.username=sa",
                    "--tracewitness.mode=MOCK",
                    "--tracewitness.auto-loop=true",
                    "--tracewitness.ai-base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                    "--tracewitness.media-dir=" + dir.resolve("media").toAbsolutePath().toString().replace('\\', '/'),
                    "--server.port=0")) {
                PlatformStore store = context.getBean(PlatformStore.class);
                Map<String, Object> agent = store.findAgentRunByRun("run-w02-mock-incident-20261110");
                List<Map<String, Object>> calls = store.listToolCalls(String.valueOf(agent.get("id")));
                List<Map<String, Object>> steps = store.listSteps(String.valueOf(agent.get("id")));
                assertEquals(2, calls.size());
                assertEquals("search_events", calls.get(0).get("toolName"));
                assertEquals("get_event_detail", calls.get(1).get("toolName"));
                assertEquals("北门摄像机记录到人员进入、停留后离开。", agent.get("conclusion"));
                assertNull(agent.get("failureType"));
                @SuppressWarnings("unchecked")
                List<String> cited = (List<String>) agent.get("sourceIds");
                assertTrue(cited.contains("tw.event.run-w02-mock-incident-20261110.000001"));
                assertTrue(!cited.contains("tw.event.other.000001"));
                assertTrue(steps.stream().anyMatch(step -> "retry".equals(step.get("stepKind"))));
            }
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
